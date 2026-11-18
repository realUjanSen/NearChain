package com.nearchain.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Live camera view that reads a QR code and returns its text to the caller as "text". */
public class ScanActivity extends Activity {
    static final String EXTRA_TEXT = "text";
    private static final int REQUEST_CAMERA = 7;
    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    private TextureView preview;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Surface previewSurface;
    private volatile boolean finished;
    private final MultiFormatReader decoder = new MultiFormatReader();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        preview = findViewById(R.id.preview);
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(com.google.zxing.BarcodeFormat.QR_CODE));
        decoder.setHints(hints);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasCameraPermission()) {
            requestCameraPermission();
            return;
        }
        startCameraThread();
        if (preview.isAvailable()) {
            openCamera();
        } else {
            preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            });
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopCameraThread();
        super.onPause();
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQUEST_CAMERA && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            onResume();
        } else {
            Toast.makeText(this, "Camera permission is needed to scan QR codes.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void startCameraThread() {
        cameraThread = new HandlerThread("nearchain-camera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopCameraThread() {
        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
            cameraHandler = null;
        }
    }

    private void openCamera() {
        if (cameraDevice != null || cameraHandler == null) {
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String backCamera = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    backCamera = id;
                    break;
                }
            }
            if (backCamera == null) {
                Toast.makeText(this, "This phone has no back camera.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            manager.openCamera(backCamera, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    createSession();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                }
            }, cameraHandler);
        } catch (CameraAccessException | SecurityException e) {
            Toast.makeText(this, "Could not open the camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void createSession() {
        try {
            SurfaceTexture texture = preview.getSurfaceTexture();
            texture.setDefaultBufferSize(WIDTH, HEIGHT);
            previewSurface = new Surface(texture);
            imageReader = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(this::onFrame, cameraHandler);
            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                CaptureRequest.Builder request =
                                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                request.addTarget(previewSurface);
                                request.addTarget(imageReader.getSurface());
                                request.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                session.setRepeatingRequest(request.build(), null, cameraHandler);
                            } catch (CameraAccessException e) {
                                finish();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            finish();
                        }
                    }, cameraHandler);
        } catch (CameraAccessException e) {
            finish();
        }
    }

    /** Runs on the camera thread for each frame. Only the brightness (Y) plane is needed for QR codes. */
    private void onFrame(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) {
            return;
        }
        try {
            if (finished) {
                return;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int rowStride = plane.getRowStride();
            byte[] luminance = new byte[width * height];
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    luminance[row * width + col] = buffer.get(row * rowStride + col);
                }
            }
            LuminanceSource source = new PlanarYUVLuminanceSource(
                    luminance, width, height, 0, 0, width, height, false);
            Result result = decoder.decodeWithState(new BinaryBitmap(new HybridBinarizer(source)));
            finished = true;
            String text = result.getText();
            runOnUiThread(() -> {
                Intent data = new Intent().putExtra(EXTRA_TEXT, text);
                setResult(RESULT_OK, data);
                finish();
            });
        } catch (com.google.zxing.NotFoundException e) {
            // No QR code in this frame yet. Keep scanning.
        } finally {
            image.close();
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }
    }
}

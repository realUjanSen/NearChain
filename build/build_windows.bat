@echo off
REM Builds NearChain.exe into dist\NearChain with the config folder beside it.
REM Requires Python 3.10+ on PATH. Run from the project root.
REM Hi
python -m pip install -r requirements.txt pyinstaller || goto :error
python -m PyInstaller --noconfirm --onedir --windowed --name NearChain ^
  --paths src desktop\nearchain_desktop.py || goto :error
xcopy /E /I /Y config dist\NearChain\config >nul
echo.
echo Build finished: dist\NearChain\NearChain.exe
exit /b 0

:error
echo Build failed.
exit /b 1

@echo off
echo spa-client serving on http://localhost:3100
cd /d "%~dp0..\spa\spa-client"
python -m http.server 3100

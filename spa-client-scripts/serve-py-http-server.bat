@echo off
echo spa-client serving on http://localhost:3000
cd /d "%~dp0..\spa-client"
python -m http.server 3000

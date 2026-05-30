@echo off
echo spa-client serving on http://localhost:3100
cd /d "%~dp0..\spa\spa-client"
npx http-server -p 3100 -c-1

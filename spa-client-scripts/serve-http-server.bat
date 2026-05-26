@echo off
echo spa-client serving on http://localhost:3000
cd /d "%~dp0..\spa-client"
npx http-server -p 3000 -c-1

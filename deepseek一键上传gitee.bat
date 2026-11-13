@echo off
chcp 65001 > nul
title RBMK-Monitor Auto Push

cd /d "%~dp0"
echo [System] Current directory: %cd%

if not exist ".git" (
    echo [Error] .git not found! Put this bat in the project folder.
    pause
    exit /b
)

set LAST_COMMIT_HASH=0c15cf80b8fbe9c0e04054a18ebd58ec

echo [1/3] Adding files...
git add .

echo [2/3] Committing...
git commit -m "feat: Auto update (hash: %LAST_COMMIT_HASH%)"

echo [3/3] Force pushing to Gitee...
git push -u origin master -f

if %errorlevel% neq 0 (
    echo ? Push failed! Check network or token.
) else (
    echo ? Push successful! Code is safe.
)

echo.
pause
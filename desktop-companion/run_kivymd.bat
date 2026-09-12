@echo off
title GLYPHIX - Material Desktop Companion (KivyMD)
echo Starting GLYPHIX Material Desktop Companion...

py -3.12 desktop_companion_kivymd.py 2>nul
if %ERRORLEVEL% NEQ 0 (
    python desktop_companion_kivymd.py
)
pause

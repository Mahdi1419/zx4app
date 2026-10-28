#!/usr/bin/env bash
# دانلود فونت وزیرمتن (Vazirmatn) در صورت نبودن
set -e
D=app/src/main/res/font
mkdir -p $D
B=https://raw.githubusercontent.com/rastikerdar/vazirmatn/master/fonts/ttf
[ -s $D/vazirmatn_regular.ttf ] || curl -fsSL $B/Vazirmatn-Regular.ttf -o $D/vazirmatn_regular.ttf
[ -s $D/vazirmatn_bold.ttf ]    || curl -fsSL $B/Vazirmatn-Bold.ttf    -o $D/vazirmatn_bold.ttf

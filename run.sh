#!/usr/bin/env bash
./doop -j ~/android/com.facebook.katana_v86.0.0.13.69-33840476_Android-5.0.apk -a context-insensitive  --enable-reflection --enable-reflection-string-flow-analysis --enable-reflection-substring-analysis --enable-reflection-invent-unknown-objects --platform android_23 --timeout 600 --cache -ldebug

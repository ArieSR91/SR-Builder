# SR-Builder Cleo Editor for Android

An unofficial Sanny Builder port for Android, written in Java. It allows you to write, edit, and manage CLEO scripts directly on your Android device.

## Features

- Write and edit CLEO scripts on Android
- Support for GTA: San Andreas Android CLEO scripts
- Compatible with Android versions up to Android 13
- Dark Theme for editor
- Syntax highlighting using Kali Linux Dark by Arie-SR91 (Me)

## Requirements

- Android device running Android 13 or lower
- GTA: San Andreas Android with CLEO library installed

## Dependencies

- [CodeEditor](https://github.com/ahmadaghazadeh/CodeEditor) — Lightweight code editor for Android
- [CleoEditor](https://github.com/FSSRepo/cleo-editor-android) - Cleo Editor for Android

## Usage

1. Open the app and create or import a CLEO script.
2. Edit your script using the built-in code editor.
3. Save and export the script to your GTA:SA CLEO folder.

## Compiler and decompiler

Now you can type if, jf, jump, etc. without the code in front of it.

- Parser for normal Sanny Builder commands like if, jf, jump etc.
- Parser for math oprator and expression like 2@ == 1, 2@ >= 1, etc
- And more

The code is plain Java without Android classes (package `com.fastsmartsystem.cleo`), so it can be checked on a desktop JVM: a similar engine
lives in [cleo-script-java](https://github.com/FSSRepo/cleo-script-java), whose tests decompile and recompile real `.cs` files.

## Disclaimer

This is an unofficial tool and is not affiliated with Sanny Builder or Rockstar Games.

I am not the developer of this apps, I only modified and rebuilt it from existing source code by FSSRepo.

## License

This project is open source. See the repository for license details.

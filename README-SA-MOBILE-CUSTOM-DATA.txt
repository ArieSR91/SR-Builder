SR Builder - custom GTA SA Mobile data

Custom data folder:
  /sdcard/sr-builder/sa_mobile/

The app attempts to create this folder on first launch after the required storage access is granted.
On Android 11 and newer, grant "Allow access to manage all files" in the app settings prompt.
On Android 6 through Android 10, grant read and write storage permissions.

Optional custom files read from this folder

sa_mobile:
    peds.ide
    vehicles.ide
    opcodes.txt
    SASCM.ini
    SASCM.Mobile.ini
    SASCM.CLEO.ini

sa_pc:
    peds.ide
    vehicles.ide
    opcodes.txt
    SASCM.CLEO.ini
    SASCM.CLEO+.ini
    SASCM.CLEO_.ini
    SASCM.Clipboard.ini
    SASCM.Debug.ini
    SASCM.INI
    SASCM.NewOpcodes.ini

The three SASCM INI files are loaded as additional opcode definitions for the compiler/editor.
Opcode IDs already present in the selected built-in opcode database are retained; INI entries can
add opcode IDs missing from that database. If a custom SASCM.CLEO.ini is absent, the bundled CLEO
INI is used as a fallback for the PC target.

Copy all SASCM.* and opcodes.txt from Sanny Builder to /sdcard/sr-builder/sa_pc
If you wanna edit cleo for PC

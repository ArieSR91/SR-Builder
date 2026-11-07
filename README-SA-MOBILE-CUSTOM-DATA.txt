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

Copy all SASCM.* and opcodes.txt from Sanny Builder to /sdcard/sr-builder/sa_pc
If you wanna edit cleo for PC

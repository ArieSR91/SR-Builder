SR Builder - custom GTA SA Mobile data

Custom data folder:
  /sdcard/sr-builder/sa_mobile/

The app attempts to create this folder on first launch after the required storage access is granted.
On Android 11 and newer, grant "Allow access to manage all files" in the app settings prompt.
On Android 6 through Android 10, grant read and write storage permissions.

Optional custom files read from this folder:
  peds.ide
  vehicles.ide
  opcodes.txt
  SASCM.ini
  SASCM.Mobile.ini
  SASCM.CLEO.ini

The three SASCM INI files are loaded as additional opcode definitions for the compiler/editor.
Opcode IDs already present in the selected built-in opcode database are retained; INI entries can
add opcode IDs missing from that database. If a custom SASCM.CLEO.ini is absent, the bundled CLEO
INI is used as a fallback for the PC target.

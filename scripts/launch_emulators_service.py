import os
import subprocess
import time
import sys

def main():
    local_app_data = os.environ.get("LOCALAPPDATA", "")
    emulator_exe = os.path.join(local_app_data, "Android", "Sdk", "emulator", "emulator.exe")
    emulator_dir = os.path.join(local_app_data, "Android", "Sdk", "emulator")
    
    if not os.path.exists(emulator_exe):
        print(f"[ERROR] Emulator executable not found at: {emulator_exe}")
        sys.exit(1)
        
    avds = [
        "Pixel_10_Pro_XL",
        "Galaxy_S25_Ultra",
        "Galaxy_Tab_S7_Plus",
        "Galaxy_A54_5G"
    ]
    
    DETACHED_PROCESS = 0x00000008
    CREATE_NEW_PROCESS_GROUP = 0x00000200
    flags = DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP
    
    print("==================================================================")
    print(" Spawning 4 Android Emulators in Detached GUI Windows...")
    print("==================================================================")
    
    for idx, avd in enumerate(avds, start=1):
        print(f"[{idx}/4] Launching {avd}...")
        cmd = [emulator_exe, "-avd", avd]
        proc = subprocess.Popen(
            cmd,
            cwd=emulator_dir,
            creationflags=flags,
            close_fds=True
        )
        print(f"       -> Dispatched with PID {proc.pid}")
        time.sleep(4)
        
    print("\nAll 4 emulators launched successfully in independent GUI windows!")

if __name__ == "__main__":
    main()


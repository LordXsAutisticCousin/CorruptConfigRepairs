# CorruptConfigAnnihilator

A Fabric 1.20.1 `preLaunch` utility that fixes broken or corrupted configuration files before the game starts, preventing startup crashes.

---

## How It Works

When launching Minecraft (`preLaunch`):
1. **Checks Configs:** Scans `config/` for unreadable or corrupted files (`.json`, `.json5`, `.jsonc`, `.toml`, `.snbt`, `.properties`, `.cfg`).
2. **Backs Up Broken Files:** Moves any corrupted file into `config_doctor_backups/<timestamp>/<relative_path>`.
3. **Restores Clean Configs:** 
   - If an uncorrupted config exists in `config/corruptconfigannihilator/defaults/`, it is copied to the live path to restore intended modpack settings.
   - If no default exists, the path stays empty so the mod can regenerate a fresh default file.
4. **Logs Actions:** Records all restorations and quarantines in `logs/corruptconfigannihilator.log`.

---

## How to Add Uncorrupted Configs

Place a copy of your clean, working config files inside `config/corruptconfigannihilator/defaults/` using the exact same folder structure as `config/`.

### 1. Folder Path
```text
config/corruptconfigannihilator/defaults/
```

### 2. Path Mirroring Example

| Live Config Path | Uncorrupted Config Path |
| :--- | :--- |
| `config/puffish_skills/skills.json` | `config/corruptconfigannihilator/defaults/puffish_skills/skills.json` |
| `config/prominent/combat_leap_jumps.json5` | `config/corruptconfigannihilator/defaults/prominent/combat_leap_jumps.json5` |
| `config/techreborn/machines.json` | `config/corruptconfigannihilator/defaults/techreborn/machines.json` |
| `config/ftbquests/client.snbt` | `config/corruptconfigannihilator/defaults/ftbquests/client.snbt` |
| `config/general.toml` | `config/corruptconfigannihilator/defaults/general.toml` |

### 3. Example Structure

```text
<Minecraft Game Directory>/
└── config/
    ├── corruptconfigannihilator/
    │   └── defaults/
    │       ├── puffish_skills/
    │       │   └── skills.json             <-- Uncorrupted Config
    │       ├── prominent/
    │       │   └── combat_leap_jumps.json5   <-- Uncorrupted Config
    │       └── general.toml                <-- Uncorrupted Config
    ├── puffish_skills/
    │   └── skills.json                     <-- Live Active Config
    ├── prominent/
    │   └── combat_leap_jumps.json5         <-- Live Active Config
    └── general.toml                        <-- Live Active Config
```

---

## Quick Copy Commands

To copy all your current working configs into `defaults/`:

### Windows (PowerShell)
```powershell
New-Item -ItemType Directory -Force -Path "config/corruptconfigannihilator/defaults"
Get-ChildItem -Path "config" -Exclude "corruptconfigannihilator" | Copy-Item -Destination "config/corruptconfigannihilator/defaults" -Recurse -Force
```

### Linux / macOS (Bash)
```bash
mkdir -p config/corruptconfigannihilator/defaults
rsync -av --exclude='corruptconfigannihilator' config/ config/corruptconfigannihilator/defaults/
```

---

## Key Notes

- **Working configs are untouched:** Healthy files are never overwritten, keeping player keybinds and settings intact.
- **Skipped folders:** `config/corruptconfigannihilator/`, `jei/`, `rei/`, `emi/`, and `spark/` are skipped automatically.
- **No jar rebuilding:** Clean configs live directly on disk in the modpack files.

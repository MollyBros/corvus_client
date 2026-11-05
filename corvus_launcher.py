"""
Corvus Launcher - отдельный лаунчер для Corvus Client (Fabric 1.21.11).

Что делает:
  * вход через официальный аккаунт Microsoft;
  * ставит Minecraft 1.21.11 + Fabric Loader + нужную Java;
  * скачивает Fabric API с Modrinth;
  * копирует Corvus Client из папки mods/ рядом с лаунчером;
  * запускает игру.
"""
import json
import os
import shutil
import subprocess
import sys
import threading
import urllib.parse
import urllib.request
import webbrowser
from pathlib import Path
import tkinter as tk
from tkinter import messagebox, simpledialog

import minecraft_launcher_lib as mll

MC_VERSION = "1.21.11"
APP_NAME = "Corvus Launcher"
APP_VERSION = "1.0.0"

APP_DIR = Path(os.environ.get("APPDATA", str(Path.home()))) / ".corvus"
GAME_DIR = APP_DIR / "game"
CONFIG_FILE = APP_DIR / "launcher.json"
BASE_DIR = Path(sys.executable).parent if getattr(sys, "frozen", False) else Path(__file__).resolve().parent
MODS_SRC = BASE_DIR / "mods"
# ресурсы (иконка/аватар): внутри exe (PyInstaller) или рядом со скриптом
RES_DIR = Path(getattr(sys, "_MEIPASS", BASE_DIR)) / "assets"

BG = "#070D1C"
PANEL = "#0E1830"
ACCENT = "#3B82F6"
CYAN = "#55E0FF"
DIM = "#8FA6C8"
WHITE = "#FFFFFF"

DEFAULT_CONFIG = {
    "client_id": "",
    "redirect_uri": "https://login.live.com/oauth20_desktop.srf",
    "ram_gb": 4,
    "refresh_token": "",
    "username": "",
    "uuid": "",
}


# ---------------------------------------------------------------- конфиг
def load_config():
    cfg = dict(DEFAULT_CONFIG)
    try:
        cfg.update(json.loads(CONFIG_FILE.read_text(encoding="utf-8")))
    except Exception:
        pass
    return cfg


def save_config(cfg):
    APP_DIR.mkdir(parents=True, exist_ok=True)
    CONFIG_FILE.write_text(json.dumps(cfg, indent=2), encoding="utf-8")


# ---------------------------------------------------------------- Modrinth
def http_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": f"CorvusLauncher/{APP_VERSION}"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def ensure_fabric_api(mods_dir: Path, set_status):
    """Скачивает свежий Fabric API под MC_VERSION, если его ещё нет."""
    set_status("Проверка Fabric API...")
    q = urllib.parse.urlencode({
        "game_versions": json.dumps([MC_VERSION]),
        "loaders": json.dumps(["fabric"]),
    })
    versions = http_json(f"https://api.modrinth.com/v2/project/fabric-api/version?{q}")
    if not versions:
        raise RuntimeError(f"Fabric API для {MC_VERSION} не найден на Modrinth")
    f = next((x for x in versions[0]["files"] if x.get("primary")), versions[0]["files"][0])
    target = mods_dir / f["filename"]
    if target.exists():
        return
    for old in mods_dir.glob("fabric-api-*.jar"):
        old.unlink(missing_ok=True)
    set_status("Скачивание Fabric API...")
    req = urllib.request.Request(f["url"], headers={"User-Agent": f"CorvusLauncher/{APP_VERSION}"})
    with urllib.request.urlopen(req, timeout=60) as r, open(target, "wb") as out:
        shutil.copyfileobj(r, out)


def install_corvus_mod(mods_dir: Path):
    jars = sorted(MODS_SRC.glob("corvus-client*.jar"))
    if not jars:
        raise RuntimeError(f"Положите corvus-client-*.jar в папку:\n{MODS_SRC}")
    for old in mods_dir.glob("corvus-client*.jar"):
        old.unlink(missing_ok=True)
    shutil.copy2(jars[-1], mods_dir / jars[-1].name)


# ---------------------------------------------------------------- GUI
class Launcher:
    W, H = 880, 500

    def __init__(self):
        self.cfg = load_config()
        self.busy = False
        self.pending = None  # (url, state, verifier) для входа

        # чтобы в панели задач Windows была иконка Corvus, а не Python
        try:
            import ctypes
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("corvus.launcher")
        except Exception:
            pass

        self.root = tk.Tk()
        self.root.title(f"{APP_NAME} {APP_VERSION}")
        self.set_window_icon()
        self.root.geometry(f"{self.W}x{self.H}")
        self.root.resizable(False, False)
        self.root.configure(bg=BG)

        self.cv = tk.Canvas(self.root, width=self.W, height=self.H, bg=BG, highlightthickness=0)
        self.cv.pack()

        self.progress = 0.0
        self.draw_static()
        self.refresh_account_label()

    def set_window_icon(self):
        """Иконка с буквой C: заголовок окна, панель задач, alt-tab."""
        try:
            self.icon_img = tk.PhotoImage(file=str(RES_DIR / "corvus_icon.png"))
            self.root.iconphoto(True, self.icon_img)
        except Exception:
            self.icon_img = None
        try:
            self.root.iconbitmap(str(RES_DIR / "corvus.ico"))  # только Windows
        except Exception:
            pass
        try:
            self.avatar_img = tk.PhotoImage(file=str(RES_DIR / "corvus_avatar_36.png"))
        except Exception:
            self.avatar_img = None

    # ---------- рисование ----------
    def rrect(self, x1, y1, x2, y2, r, **kw):
        pts = [x1 + r, y1, x2 - r, y1, x2, y1, x2, y1 + r, x2, y2 - r, x2, y2,
               x2 - r, y2, x1 + r, y2, x1, y2, x1, y2 - r, x1, y1 + r, x1, y1]
        return self.cv.create_polygon(pts, smooth=True, **kw)

    def draw_static(self):
        cv = self.cv
        # мягкое свечение фона
        for i, col in enumerate(["#09122A", "#0B1632", "#0D1A3A", "#0F1F44"]):
            r = 330 - i * 55
            cv.create_oval(230 - r, 250 - r, 230 + r, 250 + r, fill=col, outline="")

        # большая буква C (дуга с эффектом свечения)
        cx, cy, R = 230, 250, 135
        for width, col in ((74, "#0E2552"), (62, "#123171"), (52, "#1C4796")):
            cv.create_arc(cx - R, cy - R, cx + R, cy + R, start=42, extent=276,
                          style=tk.ARC, outline=col, width=width)
        cv.create_arc(cx - R, cy - R, cx + R, cy + R, start=42, extent=276,
                      style=tk.ARC, outline=ACCENT, width=40)
        cv.create_arc(cx - R + 14, cy - R + 14, cx + R - 14, cy + R - 14, start=100, extent=90,
                      style=tk.ARC, outline="#7FB0FF", width=3)

        # заголовок
        cv.create_text(490, 80, text="CORVUS", anchor="w", fill=WHITE, font=("Segoe UI", 44, "bold"))
        cv.create_text(494, 135, text="C L I E N T", anchor="w", fill=CYAN, font=("Segoe UI", 20, "bold"))
        self.rrect(494, 160, 560, 164, 2, fill=ACCENT, outline="")
        cv.create_text(494, 186, text=f"Fabric {MC_VERSION}", anchor="w", fill=DIM, font=("Segoe UI", 11))

        # аккаунт
        self.rrect(490, 215, 840, 265, 10, fill=PANEL, outline="")
        if self.avatar_img:
            cv.create_image(498, 240, image=self.avatar_img, anchor="w")
        self.account_text = cv.create_text(546, 240, anchor="w", fill=WHITE, font=("Segoe UI", 12, "bold"), text="")
        self.login_btn = self.make_button(740, 226, 826, 254, "Войти", self.on_login, small=True)

        # память
        cv.create_text(494, 292, text="Память (ГБ)", anchor="w", fill=DIM, font=("Segoe UI", 10))
        self.ram_label = cv.create_text(840, 292, text=f"{self.cfg['ram_gb']} GB", anchor="e",
                                        fill=CYAN, font=("Segoe UI", 10, "bold"))
        self.ram = tk.Scale(self.root, from_=2, to=16, orient="horizontal", showvalue=0, length=346,
                            bg=BG, troughcolor=PANEL, fg=WHITE, highlightthickness=0, bd=0,
                            activebackground=ACCENT, sliderrelief="flat", command=self.on_ram)
        self.ram.set(int(self.cfg["ram_gb"]))
        cv.create_window(490, 304, anchor="nw", window=self.ram)

        # кнопка Играть
        self.play_btn = self.make_button(490, 350, 840, 400, "ИГРАТЬ", self.on_play, big=True)

        # прогресс и статус
        self.rrect(490, 418, 840, 426, 4, fill=PANEL, outline="")
        self.bar = self.rrect(490, 418, 494, 426, 4, fill=ACCENT, outline="")
        self.status = cv.create_text(490, 446, anchor="w", fill=DIM, font=("Segoe UI", 10), text="Готов к запуску")

        # настройки
        link = cv.create_text(840, 480, text="Настройки входа", anchor="e", fill=DIM, font=("Segoe UI", 9, "underline"))
        cv.tag_bind(link, "<Button-1>", lambda e: self.on_settings())
        cv.create_text(20, 480, text=f"v{APP_VERSION}", anchor="w", fill="#41557A", font=("Segoe UI", 9))

    def make_button(self, x1, y1, x2, y2, label, command, big=False, small=False):
        fill = ACCENT
        shape = self.rrect(x1, y1, x2, y2, 12 if big else 8, fill=fill, outline="")
        text = self.cv.create_text((x1 + x2) // 2, (y1 + y2) // 2, text=label, fill=WHITE,
                                   font=("Segoe UI", 15 if big else 10, "bold"))
        def enter(_): self.cv.itemconfig(shape, fill="#5B9BFF")
        def leave(_): self.cv.itemconfig(shape, fill=ACCENT)
        for item in (shape, text):
            self.cv.tag_bind(item, "<Enter>", enter)
            self.cv.tag_bind(item, "<Leave>", leave)
            self.cv.tag_bind(item, "<Button-1>", lambda e: command())
        return shape, text

    # ---------- состояние ----------
    def ui(self, fn, *a):
        self.root.after(0, lambda: fn(*a))

    def set_status(self, text):
        self.ui(self.cv.itemconfig, self.status, {"text": text})

    def set_progress(self, frac):
        frac = max(0.0, min(1.0, frac))
        def draw():
            self.cv.coords(self.bar, *self._bar_points(frac))
        self.ui(draw)

    def _bar_points(self, frac):
        x1, y1, x2, y2, r = 490, 418, 490 + max(8, int(350 * frac)), 426, 4
        return [x1 + r, y1, x2 - r, y1, x2, y1, x2, y1 + r, x2, y2 - r, x2, y2,
                x2 - r, y2, x1 + r, y2, x1, y2, x1, y2 - r, x1, y1 + r, x1, y1]

    def refresh_account_label(self):
        name = self.cfg.get("username")
        self.cv.itemconfig(self.account_text, text=f"Игрок: {name}" if name else "Вход не выполнен")
        self.cv.itemconfig(self.login_btn[1], text="Сменить" if name else "Войти")

    def on_ram(self, v):
        self.cfg["ram_gb"] = int(float(v))
        self.cv.itemconfig(self.ram_label, text=f"{self.cfg['ram_gb']} GB")
        save_config(self.cfg)

    # ---------- настройки / вход ----------
    def on_settings(self):
        cid = simpledialog.askstring(
            "Настройки входа",
            "Application (client) ID вашего приложения в Azure.\n"
            "Регистрация: portal.azure.com -> App registrations.\n"
            "Тип: Mobile and desktop, Redirect URI:\n"
            f"{self.cfg['redirect_uri']}",
            initialvalue=self.cfg.get("client_id", ""), parent=self.root)
        if cid is not None:
            self.cfg["client_id"] = cid.strip()
            save_config(self.cfg)

    def on_login(self):
        if not self.cfg.get("client_id"):
            messagebox.showinfo(APP_NAME, "Сначала укажите Client ID в «Настройки входа».")
            self.on_settings()
            if not self.cfg.get("client_id"):
                return
        try:
            url, state, verifier = mll.microsoft_account.get_secure_login_data(
                self.cfg["client_id"], self.cfg["redirect_uri"])
        except Exception as e:
            messagebox.showerror(APP_NAME, f"Не удалось начать вход:\n{e}")
            return
        webbrowser.open(url)
        answer = simpledialog.askstring(
            "Вход через Microsoft",
            "Войдите в браузере. Когда страница станет пустой,\n"
            "скопируйте ПОЛНЫЙ адрес из адресной строки и вставьте сюда:",
            parent=self.root)
        if not answer:
            return
        self.set_status("Вход...")
        threading.Thread(target=self._finish_login, args=(answer.strip(), state, verifier), daemon=True).start()

    def _finish_login(self, url, state, verifier):
        try:
            code = mll.microsoft_account.parse_auth_code_url(url, state)
            data = mll.microsoft_account.complete_login(
                self.cfg["client_id"], None, self.cfg["redirect_uri"], code, verifier)
            self.cfg.update(username=data["name"], uuid=data["id"], refresh_token=data["refresh_token"])
            self._session = data
            save_config(self.cfg)
            self.ui(self.refresh_account_label)
            self.set_status("Вход выполнен")
        except Exception as e:
            self.set_status("Ошибка входа")
            self.ui(messagebox.showerror, APP_NAME, f"Не удалось войти:\n{e}")

    def get_session(self):
        """Свежая сессия: по refresh-токену (без повторного ввода)."""
        if not self.cfg.get("refresh_token"):
            raise RuntimeError("Сначала войдите в аккаунт Microsoft (кнопка «Войти»).")
        data = mll.microsoft_account.complete_refresh(
            self.cfg["client_id"], None, self.cfg["redirect_uri"], self.cfg["refresh_token"])
        self.cfg.update(username=data["name"], uuid=data["id"], refresh_token=data["refresh_token"])
        save_config(self.cfg)
        self.ui(self.refresh_account_label)
        return data

    # ---------- запуск ----------
    def on_play(self):
        if self.busy:
            return
        self.busy = True
        threading.Thread(target=self._play, daemon=True).start()

    def _play(self):
        try:
            GAME_DIR.mkdir(parents=True, exist_ok=True)
            mods_dir = GAME_DIR / "mods"
            mods_dir.mkdir(exist_ok=True)

            self.set_status("Проверка аккаунта...")
            session = self.get_session()

            total = {"max": 1}
            callback = {
                "setStatus": lambda t: self.set_status(t),
                "setMax": lambda m: total.update(max=max(1, m)),
                "setProgress": lambda p: self.set_progress(p / total["max"]),
            }

            loader = mll.fabric.get_latest_loader_version()
            version_id = f"fabric-loader-{loader}-{MC_VERSION}"
            installed = {v["id"] for v in mll.utils.get_installed_versions(str(GAME_DIR))}
            if version_id not in installed:
                self.set_status(f"Установка Minecraft {MC_VERSION} + Fabric...")
                mll.fabric.install_fabric(MC_VERSION, str(GAME_DIR), loader_version=loader, callback=callback)

            ensure_fabric_api(mods_dir, self.set_status)
            install_corvus_mod(mods_dir)

            self.set_status("Запуск игры...")
            self.set_progress(1.0)
            options = {
                "username": session["name"],
                "uuid": session["id"],
                "token": session["access_token"],
                "jvmArguments": [f"-Xmx{self.cfg['ram_gb']}G", "-Xms512M"],
                "launcherName": "CorvusLauncher",
                "launcherVersion": APP_VERSION,
            }
            cmd = mll.command.get_minecraft_command(version_id, str(GAME_DIR), options)
            proc = subprocess.Popen(cmd, cwd=str(GAME_DIR))
            self.set_status("Игра запущена")
            proc.wait()
            self.set_status("Игра закрыта")
        except Exception as e:
            self.set_status("Ошибка")
            self.ui(messagebox.showerror, APP_NAME, str(e))
        finally:
            self.busy = False
            self.set_progress(0)

    def run(self):
        self.root.mainloop()


if __name__ == "__main__":
    Launcher().run()

# -*- coding: utf-8 -*-
"""
Küfür Alarmı (PC)
Mikrofonu dinler, küfür duyunca TÜM ekranları 5 saniye kıpkırmızı kaplar.
Uyarı 5 saniye dolmadan kapatılamaz.
"""
import ctypes
import datetime
import os
import queue
import re
import sys
import threading
try:
    import tkinter as tk
except ImportError:  # sadece test modunda gerekmez
    tk = None

APP_DIR = os.path.dirname(os.path.abspath(__file__))
LOG_FILE = os.path.join(APP_DIR, "kufur_kayit.csv")
EXTRA_WORDS_FILE = os.path.join(APP_DIR, "ekstra_kelimeler.txt")
ALERT_SECONDS = 5
LANG = "tr-TR"

# ---------------------------------------------------------------------------
# Küfür tespiti
# ---------------------------------------------------------------------------
_TR_LOWER = str.maketrans({"I": "ı", "İ": "i"})


def tr_lower(text: str) -> str:
    return text.translate(_TR_LOWER).lower()


# Kalıplar Türkçe harflerle yazılır (ı ile i farklıdır: "sık" küfür değil).
_PATTERNS = [
    r"am[kq]", r"aq", r"amq", r"mk",
    r"amına\w*", r"amını", r"amcık\w*", r"amınako\w*",
    r"orospu\w*", r"oç", r"o\.ç", r"piç\w*",
    r"(?!sikke)sik\w*", r"hassik\w*", r"siktir\w*", r"s\.ktir\w*",
    r"yarr?ak\w*", r"dalyarak\w*", r"yavşak\w*",
    r"göt", r"götü", r"götün\w*", r"götver\w*", r"götlek\w*", r"göte", r"götten",
    r"ibne\w*", r"pezevenk\w*", r"kahpe\w*", r"gavat\w*", r"puşt\w*",
    r"şerefsiz\w*", r"namussuz\w*", r"kaltak\w*", r"sürtük\w*",
    r"bok", r"boku", r"boktan", r"bokum\w*", r"bokun\w*",
    r"amk\w*", r"aminako\w*", r"amina\w*",  # bazı tanıyıcılar ı yerine i yazar
]


def _load_extra_words():
    words = []
    if os.path.exists(EXTRA_WORDS_FILE):
        with open(EXTRA_WORDS_FILE, encoding="utf-8") as f:
            for line in f:
                w = line.strip()
                if w and not w.startswith("#"):
                    words.append(re.escape(tr_lower(w)) + r"\w*")
    return words


def build_regex():
    pats = _PATTERNS + _load_extra_words()
    return re.compile(r"(?<!\w)(?:" + "|".join(pats) + r")(?!\w)")


KUFUR_RE = build_regex()


def find_kufur(text: str):
    """Bulunan küfürleri listeler. Tanıyıcı '*' ile sansürlediyse onu da yakalar."""
    t = tr_lower(text)
    hits = [m.group(0) for m in KUFUR_RE.finditer(t)]
    # Google bazen küfrü "s****" gibi yıldızlar. Bunu da küfür say.
    hits += re.findall(r"\w+\*+\w*", t)
    return hits


# ---------------------------------------------------------------------------
# Kayıt
# ---------------------------------------------------------------------------
def log_hit(phrase, words):
    new = not os.path.exists(LOG_FILE)
    with open(LOG_FILE, "a", encoding="utf-8-sig") as f:
        if new:
            f.write("tarih;saat;kelimeler;cumle\n")
        now = datetime.datetime.now()
        safe = phrase.replace(";", ",").replace("\n", " ")
        f.write(f"{now:%Y-%m-%d};{now:%H:%M:%S};{' '.join(words)};{safe}\n")


def today_count():
    if not os.path.exists(LOG_FILE):
        return 0
    today = datetime.date.today().isoformat()
    with open(LOG_FILE, encoding="utf-8-sig") as f:
        return sum(1 for line in f if line.startswith(today))


# ---------------------------------------------------------------------------
# Ekran
# ---------------------------------------------------------------------------
def virtual_screen():
    """Tüm monitörleri kapsayan alan (x, y, w, h)."""
    if sys.platform == "win32":
        try:
            ctypes.windll.shcore.SetProcessDpiAwareness(2)
        except Exception:
            try:
                ctypes.windll.user32.SetProcessDPIAware()
            except Exception:
                pass
        u = ctypes.windll.user32
        return (u.GetSystemMetrics(76), u.GetSystemMetrics(77),
                u.GetSystemMetrics(78), u.GetSystemMetrics(79))
    return None


class App:
    def __init__(self):
        self.vs = virtual_screen()
        self.root = tk.Tk()
        self.root.title("Küfür Alarmı")
        self.root.geometry("380x230")
        self.root.configure(bg="#1b1b1f")
        self.root.resizable(False, False)
        self.events = queue.Queue()
        self.alert_win = None
        self.listening = True

        tk.Label(self.root, text="🛑 Küfür Alarmı", fg="white", bg="#1b1b1f",
                 font=("Segoe UI", 16, "bold")).pack(pady=(14, 4))
        self.status = tk.Label(self.root, text="Mikrofon hazırlanıyor...", fg="#bbbbbb",
                               bg="#1b1b1f", font=("Segoe UI", 10))
        self.status.pack()
        self.count_lbl = tk.Label(self.root, text="", fg="#ff5c5c", bg="#1b1b1f",
                                  font=("Segoe UI", 13, "bold"))
        self.count_lbl.pack(pady=8)
        self.heard = tk.Label(self.root, text="", fg="#777", bg="#1b1b1f",
                              font=("Segoe UI", 9), wraplength=350)
        self.heard.pack()
        bar = tk.Frame(self.root, bg="#1b1b1f")
        bar.pack(pady=10)
        tk.Button(bar, text="Uyarıyı test et", command=lambda: self.show_alert(["test"]),
                  width=14).pack(side="left", padx=5)
        self.pause_btn = tk.Button(bar, text="Duraklat", command=self.toggle, width=10)
        self.pause_btn.pack(side="left", padx=5)
        self.update_count()

        threading.Thread(target=self.listen_loop, daemon=True).start()
        self.root.after(100, self.poll)

    # -- arayüz --------------------------------------------------------------
    def update_count(self):
        self.count_lbl.config(text=f"Bugünkü küfür sayısı: {today_count()}")

    def toggle(self):
        self.listening = not self.listening
        self.pause_btn.config(text="Duraklat" if self.listening else "Devam et")
        self.status.config(text="Dinleniyor 🎙️" if self.listening else "Duraklatıldı")

    def poll(self):
        try:
            while True:
                kind, data = self.events.get_nowait()
                if kind == "status":
                    self.status.config(text=data)
                elif kind == "heard":
                    self.heard.config(text=f"Son duyulan: {data}")
                elif kind == "kufur":
                    phrase, words = data
                    log_hit(phrase, words)
                    self.update_count()
                    self.show_alert(words)
        except queue.Empty:
            pass
        self.root.after(100, self.poll)

    def show_alert(self, words):
        if self.alert_win is not None:
            self.remaining = ALERT_SECONDS  # uyarı açıkken yeni küfür: süre yeniden başlar
            return
        w = tk.Toplevel(self.root)
        self.alert_win = w
        w.overrideredirect(True)
        w.configure(bg="#ff0000", cursor="none")
        if self.vs:
            x, y, sw, sh = self.vs
        else:
            x, y = 0, 0
            sw, sh = w.winfo_screenwidth(), w.winfo_screenheight()
        w.geometry(f"{sw}x{sh}+{x}+{y}")
        w.attributes("-topmost", True)
        w.protocol("WM_DELETE_WINDOW", lambda: None)  # Alt+F4 çalışmaz
        for seq in ("<Key>", "<Button>", "<Escape>", "<Alt-F4>"):
            w.bind(seq, lambda e: "break")

        frame = tk.Frame(w, bg="#ff0000")
        frame.place(relx=0.5, rely=0.5, anchor="center")
        tk.Label(frame, text="⚠ KÜFÜR ALGILANDI ⚠", fg="white", bg="#ff0000",
                 font=("Segoe UI", 64, "bold")).pack()
        self.cd_lbl = tk.Label(frame, text="", fg="white", bg="#ff0000",
                               font=("Segoe UI", 40))
        self.cd_lbl.pack(pady=20)
        tk.Label(frame, text=f"Bugün: {today_count()}. küfür", fg="#ffd6d6", bg="#ff0000",
                 font=("Segoe UI", 22)).pack()

        self.remaining = ALERT_SECONDS
        self._tick()
        self._keep_on_top()
        self.beep()

    def _tick(self):
        if self.alert_win is None:
            return
        if self.remaining <= 0:
            self.alert_win.destroy()
            self.alert_win = None
            return
        self.cd_lbl.config(text=f"{self.remaining}")
        self.remaining -= 1
        self.alert_win.after(1000, self._tick)

    def _keep_on_top(self):
        w = self.alert_win
        if w is None:
            return
        try:
            w.attributes("-topmost", True)
            w.lift()
            w.focus_force()
            if sys.platform == "win32":
                hwnd = ctypes.windll.user32.GetParent(w.winfo_id())
                # HWND_TOPMOST, SWP_NOMOVE|SWP_NOSIZE|SWP_SHOWWINDOW
                ctypes.windll.user32.SetWindowPos(hwnd, -1, 0, 0, 0, 0, 0x0001 | 0x0002 | 0x0040)
        except Exception:
            pass
        w.after(50, self._keep_on_top)

    @staticmethod
    def beep():
        if sys.platform == "win32":
            try:
                import winsound
                threading.Thread(target=lambda: winsound.Beep(880, 400), daemon=True).start()
            except Exception:
                pass

    # -- mikrofon ------------------------------------------------------------
    def listen_loop(self):
        try:
            import speech_recognition as sr
        except ImportError:
            self.events.put(("status", "speech_recognition yok. Önce Kurulum.bat çalıştır."))
            return
        r = sr.Recognizer()
        r.dynamic_energy_threshold = True
        r.pause_threshold = 0.5
        r.non_speaking_duration = 0.3
        try:
            mic = sr.Microphone()
        except Exception as e:
            self.events.put(("status", f"Mikrofon açılamadı: {e}"))
            return
        with mic as source:
            r.adjust_for_ambient_noise(source, duration=1)
        self.events.put(("status", "Dinleniyor 🎙️"))

        def worker(audio):
            try:
                text = r.recognize_google(audio, language=LANG, pfilter=0)
            except sr.UnknownValueError:
                return
            except sr.RequestError:
                self.events.put(("status", "İnternet yok, tekrar deneniyor..."))
                return
            except Exception:
                return
            self.events.put(("status", "Dinleniyor 🎙️"))
            self.events.put(("heard", text))
            hits = find_kufur(text)
            if hits:
                self.events.put(("kufur", (text, hits)))

        def callback(_rec, audio):
            if self.listening:
                threading.Thread(target=worker, args=(audio,), daemon=True).start()

        self._stop = r.listen_in_background(mic, callback, phrase_time_limit=4)

    def run(self):
        self.root.mainloop()


if __name__ == "__main__":
    if "--test" in sys.argv:
        for s in sys.argv[sys.argv.index("--test") + 1:]:
            print(repr(s), "->", find_kufur(s))
        sys.exit(0)
    App().run()

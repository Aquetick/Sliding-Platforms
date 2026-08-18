#!/usr/bin/env python3
"""Генератор зацикленного «гула» двери (slidingdoors:door_hum).
Пишет assets/slidingdoors/sounds/door_hum.ogg (OGG Vorbis, моно 44100 Hz).
Запуск: python3 scripts/gen_hum.py (нужны numpy и soundfile)."""
import numpy as np
import soundfile as sf
import os

SR = 44100
DUR = 2.0  # секунды; гул зацикливается движком, потому стык обязан быть чистым
N = int(SR * DUR)
t = np.arange(N) / SR
out = os.path.join(os.path.dirname(__file__), "..", "src", "main",
                   "resources", "assets", "slidingdoors", "sounds", "door_hum.ogg")

# Гармоника: низкий гул 56.5 Гц (ровно 113 периодов в окне -> бесшовный цикл).
f0 = 56.5
cycles = round(f0 * DUR)
f0 = cycles / DUR
hum = (np.sin(2 * np.pi * f0 * t)
       + 0.45 * np.sin(2 * np.pi * 2 * f0 * t)
       + 0.20 * np.sin(2 * np.pi * 3 * f0 * t)
       + 0.10 * np.sin(2 * np.pi * 5 * f0 * t))

# Живая пульсация: амплитудная модуляция на двух частотах (те же целые циклы).
m1 = round(6.5 * DUR) / DUR
m2 = round(13.0 * DUR) / DUR
am = (1.0 + 0.30 * np.sin(2 * np.pi * m1 * t)
      + 0.12 * np.sin(2 * np.pi * m2 * t))
hum *= am

# Механический «шорох»: коричневый шум, нормированный в бесшовный цикл
# кроссфейдом хвоста на начало (150 мс).
rng = np.random.default_rng(20260813)
white = rng.standard_normal(N)
brown = np.cumsum(white)
brown -= np.linspace(brown[0], brown[-1], N)  # убрать дрейф к концам окна
xf = int(SR * 0.15)
w = 0.5 * (1 - np.cos(np.pi * np.arange(xf) / xf))
brown[-xf:] = brown[-xf:] * (1 - w) + brown[:xf] * w
brown /= np.max(np.abs(brown))
# лёгкий «потрескивание» рельса: синус 220 Гц, взвешенный 6-й степенью 3.25-Гц окна
r_c = round(220 * DUR) / DUR
g_c = round(3.25 * DUR) / DUR
rattle = np.sin(2 * np.pi * r_c * t) * (np.abs(np.sin(2 * np.pi * g_c * t)) ** 6) * 0.25

mix = 0.62 * hum + 0.30 * brown + rattle
mix = np.tanh(mix * 1.15) * 0.72  # мягкий клиппер + запас по громкости

sf.write(out, mix.astype(np.float32), SR, format="OGG", subtype="VORBIS")
print("написано:", out, os.path.getsize(out), "байт; пик", float(np.max(np.abs(mix))))

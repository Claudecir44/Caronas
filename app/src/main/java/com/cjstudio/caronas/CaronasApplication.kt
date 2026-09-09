package com.cjstudio.caronas

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Ponto de entrada do Hilt. Fase 1: só isso — App Check, heartbeat de
// presença e proteção contra captura de tela (padrões usados no Match)
// ficam pra quando essas necessidades aparecerem de verdade aqui.
@HiltAndroidApp
class CaronasApplication : Application()

package com.example.sync

object WebSpeakerHtmlProvider {

    fun getManifestJson(roomName: String): String = """
        {
          "name": "SyncSound - ${escapeJson(roomName)}",
          "short_name": "SyncSound",
          "description": "Every Device. One Sound. Multi-device synchronized wireless speaker player.",
          "start_url": "/",
          "display": "standalone",
          "background_color": "#090C12",
          "theme_color": "#00E5FF",
          "orientation": "portrait",
          "icons": [
            {
              "src": "/icon.svg",
              "sizes": "any",
              "type": "image/svg+xml"
            }
          ]
        }
    """.trimIndent()

    val serviceWorkerJs: String = """
        const CACHE = 'syncsound-web-v2';
        self.addEventListener('install', (e) => {
          e.waitUntil(caches.open(CACHE).then((c) => c.addAll(['/', '/manifest.json', '/icon.svg'])).catch(() => {}));
          self.skipWaiting();
        });
        self.addEventListener('activate', (e) => {
          e.waitUntil(self.clients.claim());
        });
        self.addEventListener('fetch', (e) => {
          if (e.request.url.includes('/audio/') || e.request.url.includes('/ws') || e.request.url.includes('/api/')) return;
          e.respondWith(fetch(e.request).catch(() => caches.match(e.request)));
        });
    """.trimIndent()

    val iconSvg: String = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128">
          <rect width="128" height="128" rx="28" fill="#0B0F17"/>
          <circle cx="64" cy="64" r="46" fill="none" stroke="#00E5FF" stroke-width="6" opacity="0.45"/>
          <circle cx="64" cy="64" r="32" fill="none" stroke="#00E676" stroke-width="6" opacity="0.75"/>
          <polygon points="54,44 88,64 54,84" fill="#00E5FF"/>
        </svg>
    """.trimIndent()

    fun getSpeakerHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <meta name="theme-color" content="#090C12" />
  <meta name="description" content="SyncSound - Every Device. One Sound. Turn phones, tablets, and laptops into a wireless synchronized multi-speaker system." />
  <title>SyncSound — Every Device. One Sound.</title>
  <link rel="manifest" href="/manifest.json" />
  <link rel="icon" href="/icon.svg" type="image/svg+xml" />
  <style>
    :root {
      --bg: #090C12; --surface: #111622; --card: #182030; --border: #28334C;
      --cyan: #00E5FF; --green: #00E676; --amber: #FFB300; --red: #FF5252; --blue: #448AFF;
      --text: #F2F6FF; --muted: #9BA8C2;
    }
    body.light {
      --bg: #F4F7FC; --surface: #FFFFFF; --card: #E9EFF8; --border: #D0DAE8;
      --cyan: #008394; --green: #00A854; --text: #0F172A; --muted: #526079;
    }
    * { box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    body { margin: 0; background: var(--bg); color: var(--text); min-height: 100vh; }
    .container { max-width: 980px; margin: 0 auto; padding: 16px 20px 48px; }
    .nav { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 0 20px; border-bottom: 1px solid var(--border); flex-wrap: wrap; }
    .brand { display: flex; align-items: center; gap: 10px; font-weight: 800; font-size: 20px; cursor: pointer; }
    .nav-links { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
    .nav-btn { background: transparent; border: 1px solid var(--border); color: var(--text); padding: 8px 14px; border-radius: 999px; font-size: 13px; font-weight: 600; cursor: pointer; }
    .nav-btn.active { background: var(--cyan); color: #002026; border-color: var(--cyan); }
    .card { background: var(--surface); border: 1px solid var(--border); border-radius: 22px; padding: 22px; margin-top: 18px; }
    .hero { background: linear-gradient(135deg, rgba(0,229,255,0.14), rgba(0,230,118,0.08)), var(--surface); padding: 32px 24px; }
    h1 { font-size: clamp(26px, 5vw, 40px); margin: 0 0 10px; letter-spacing: -0.6px; }
    h2 { font-size: 20px; margin: 0 0 8px; }
    h3 { font-size: 16px; margin: 0 0 6px; }
    p { margin: 0; }
    .muted { color: var(--muted); font-size: 14px; line-height: 1.55; }
    .mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; margin-top: 16px; }
    .tile { background: var(--card); border-radius: 16px; padding: 16px; }
    .row { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
    .btn { padding: 13px 18px; border-radius: 14px; border: none; font-size: 15px; font-weight: 700; cursor: pointer; }
    .btn-primary { background: linear-gradient(135deg, #00E5FF, #00B8D4); color: #002026; }
    .btn-secondary { background: var(--card); color: var(--text); border: 1px solid var(--border); }
    .btn-danger { background: rgba(255,82,82,0.16); color: var(--red); border: 1px solid rgba(255,82,82,0.4); width: 100%; margin-top: 14px; }
    input[type=text], input[type=search] { width: 100%; padding: 13px 15px; border-radius: 14px; border: 1px solid var(--border); background: var(--bg); color: var(--text); font-size: 15px; margin-top: 10px; }
    input[type=range] { width: 100%; accent-color: var(--cyan); margin: 8px 0; }
    .badge { padding: 5px 12px; border-radius: 999px; font-size: 12px; font-weight: 700; background: var(--card); color: var(--cyan); }
    .badge.green { background: rgba(0,230,118,0.16); color: var(--green); }
    .badge.yellow { background: rgba(255,179,0,0.16); color: var(--amber); }
    .badge.red { background: rgba(255,82,82,0.16); color: var(--red); }
    .hidden { display: none !important; }
    details { background: var(--card); border-radius: 14px; padding: 14px 16px; margin-top: 10px; }
    summary { font-weight: 700; cursor: pointer; }
    #ytWrap { margin-top: 12px; border-radius: 14px; overflow: hidden; aspect-ratio: 16/9; background: #000; }
    .footer { margin-top: 28px; padding-top: 18px; border-top: 1px solid var(--border); text-align: center; }
  </style>
</head>
<body>
  <div class="container">
    <nav class="nav">
      <div class="brand" onclick="showPage('home')">
        <svg width="32" height="32" viewBox="0 0 128 128">
          <rect width="128" height="128" rx="28" fill="#0B0F17"/>
          <circle cx="64" cy="64" r="44" fill="none" stroke="#00E5FF" stroke-width="7" opacity="0.5"/>
          <circle cx="64" cy="64" r="28" fill="none" stroke="#00E676" stroke-width="7"/>
          <polygon points="56,48 84,64 56,80" fill="#00E5FF"/>
        </svg>
        <div>
          <div>SyncSound</div>
          <div class="muted" style="font-size:11px;">Every Device. One Sound.</div>
        </div>
      </div>
      <div class="nav-links">
        <button class="nav-btn active" id="nav-home" onclick="showPage('home')">Home</button>
        <button class="nav-btn" id="nav-join" onclick="showPage('join')">Join Room</button>
        <button class="nav-btn hidden" id="nav-speaker" onclick="showPage('speaker')">Speaker</button>
        <button class="nav-btn" id="nav-settings" onclick="showPage('settings')">Settings</button>
        <span id="statusBadge" class="badge">READY</span>
      </div>
    </nav>

    <!-- PAGE 1: HOME -->
    <section id="page-home">
      <div class="card hero">
        <span class="badge" style="margin-bottom:12px; display:inline-block;">LAN WEB &amp; PWA SPEAKER</span>
        <h1>Turn Every Device Into a Speaker.</h1>
        <p class="muted" style="max-width:620px; margin-bottom:20px;">
          Connect your phones, tablets and laptops. Play music together in perfect sync using scheduled WebSocket playback timestamps and NTP clock offset filtering.
        </p>
        <div style="display:flex; gap:12px; flex-wrap:wrap;">
          <button class="btn btn-primary" onclick="showPage('join')">Join Music Room</button>
          <button class="btn btn-secondary" onclick="document.getElementById('howItWorks').scrollIntoView({behavior:'smooth'})">How It Works</button>
        </div>
      </div>

      <div class="card" id="howItWorks">
        <h2>How It Works</h2>
        <div class="grid">
          <div class="tile"><h3>1. Host on Admin</h3><p class="muted">The Admin Android device generates a unique 5-digit room code, QR invite, and local HTTP audio stream.</p></div>
          <div class="tile"><h3>2. Join Any Device</h3><p class="muted">Phones, tablets, Windows laptops, and macOS browsers join via 5-digit code or shared URL.</p></div>
          <div class="tile"><h3>3. Synchronized Clock</h3><p class="muted">NTP-style pings measure round-trip latency and clock offset before scheduling future playback timestamps.</p></div>
          <div class="tile"><h3>4. Individual Control</h3><p class="muted">Adjust speaker volume, mute state, and hardware delay calibration (-250ms to +250ms) per device.</p></div>
        </div>
      </div>

      <div class="card">
        <h2>Supported Devices &amp; Multi-Speaker Architecture</h2>
        <div class="grid">
          <div class="tile"><h3>Mobile &amp; Tablets</h3><p class="muted">Android Chrome, Android Firefox, and native SyncSound Android app.</p></div>
          <div class="tile"><h3>Desktop &amp; Laptops</h3><p class="muted">Windows Chrome/Edge/Firefox and macOS modern browsers with PWA install support.</p></div>
        </div>
        <p class="muted" style="margin-top:14px;">Note: SyncSound provides synchronized wireless multi-speaker stereo/mono playback, not discrete Dolby surround channels.</p>
      </div>

      <div class="card">
        <h2>Frequently Asked Questions</h2>
        <details><summary>Do speakers need an account to join?</summary><p class="muted" style="margin-top:8px;">No. Speakers connect via anonymous session tokens using the 5-digit room code and custom device name.</p></details>
        <details><summary>Why do I tap "Arm Audio" in a browser?</summary><p class="muted" style="margin-top:8px;">Modern mobile and desktop browsers block unmuted autoplay until the user interacts with the page.</p></details>
        <details><summary>How accurate is synchronization?</summary><p class="muted" style="margin-top:8px;">On a clean local Wi-Fi network, SyncSound targets ~50–100ms alignment via scheduled timestamps and continuous drift nudging. Actual variance depends on Wi-Fi congestion and Bluetooth/audio hardware latency.</p></details>
      </div>
    </section>

    <!-- PAGE 3: JOIN ROOM -->
    <section id="page-join" class="hidden">
      <div class="card" style="max-width:520px; margin:24px auto;">
        <h2>Join Music Room</h2>
        <p class="muted">Enter the 5-digit room code and a custom speaker name for this device.</p>
        <input id="roomCodeInput" type="text" maxlength="5" placeholder="5-Digit Room Code (e.g. 58321)" />
        <input id="deviceNameInput" type="text" placeholder="Device Name (e.g. Windows Laptop)" />
        <input id="tokenInput" type="hidden" />
        <button id="joinBtn" class="btn btn-primary" style="width:100%; margin-top:14px;">Connect &amp; Arm Audio</button>
        <p id="joinError" class="muted" style="color:var(--red); margin-top:10px;"></p>
      </div>
    </section>

    <!-- PAGE 5: SPEAKER DASHBOARD -->
    <section id="page-speaker" class="hidden">
      <div class="card" style="max-width:560px; margin:20px auto;">
        <div class="row">
          <div>
            <div class="muted">CONNECTED ROOM</div>
            <h2 id="roomNameLabel">--</h2>
            <div id="adminNameLabel" class="muted">Admin: --</div>
          </div>
          <span id="roomCodeBadge" class="badge mono">-----</span>
        </div>

        <div class="tile" style="margin-top:16px;">
          <div class="muted mono" id="sourceTag">STANDBY</div>
          <div id="trackTitle" style="font-size:20px; font-weight:800; margin-top:4px;">Waiting for Admin...</div>
          <div id="trackArtist" class="muted" style="margin-top:2px;">Ready for synchronized playback</div>
          <div id="ytWrap" class="hidden"><div id="ytPlayer"></div></div>
        </div>

        <button id="unlockAudioBtn" class="btn btn-secondary" style="width:100%; margin-top:12px;">Tap to Arm Browser Audio</button>

        <div style="margin-top:16px;">
          <div class="row"><span class="muted">Local Volume</span><span id="volPct" class="mono">100%</span></div>
          <input id="volumeSlider" type="range" min="0" max="100" value="100" />
        </div>

        <div style="margin-top:10px;">
          <div class="row"><span class="muted">Hardware Delay Calibration</span><span id="delayLabel" class="mono">0 ms</span></div>
          <input id="delaySlider" type="range" min="-250" max="250" value="0" />
        </div>

        <div class="grid" style="grid-template-columns:repeat(3,1fr); margin-top:14px;">
          <div class="tile" style="text-align:center;"><div class="muted">RTT</div><div id="rttVal" class="mono" style="font-weight:700; color:var(--cyan);">-- ms</div></div>
          <div class="tile" style="text-align:center;"><div class="muted">OFFSET</div><div id="offsetVal" class="mono" style="font-weight:700; color:var(--cyan);">-- ms</div></div>
          <div class="tile" style="text-align:center;"><div class="muted">DRIFT</div><div id="driftVal" class="mono" style="font-weight:700; color:var(--cyan);">-- ms</div></div>
        </div>

        <button id="leaveBtn" class="btn btn-danger">Leave Room</button>
      </div>
    </section>

    <!-- PAGE 6: SETTINGS -->
    <section id="page-settings" class="hidden">
      <div class="card" style="max-width:560px; margin:20px auto;">
        <h2>Browser Speaker Settings</h2>
        <div class="row" style="margin-top:14px;">
          <div>
            <h3>Appearance Theme</h3>
            <p class="muted">Dark acoustic mode by default, or switch to light mode.</p>
          </div>
          <button id="themeToggleBtn" class="btn btn-secondary" onclick="toggleTheme()">Toggle Light / Dark</button>
        </div>
        <div style="margin-top:16px;">
          <h3>Install as PWA</h3>
          <p class="muted">In Chrome or Edge, choose "Install SyncSound" from the browser menu or address bar to launch this speaker as a standalone app.</p>
        </div>
      </div>
    </section>

    <footer class="footer">
      <p class="muted">SyncSound · Every Device. One Sound. · Hosted live from your Admin room node.</p>
    </footer>
  </div>

  <audio id="audioEl" preload="auto" crossorigin="anonymous"></audio>
  <script>
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('/sw.js').catch(() => {});
    }

    if (localStorage.getItem('syncsound_theme') === 'light') {
      document.body.classList.add('light');
    }

    function toggleTheme() {
      document.body.classList.toggle('light');
      localStorage.setItem('syncsound_theme', document.body.classList.contains('light') ? 'light' : 'dark');
    }

    function showPage(name) {
      ['home', 'join', 'speaker', 'settings'].forEach((p) => {
        document.getElementById('page-' + p).classList.toggle('hidden', p !== name);
        const btn = document.getElementById('nav-' + p);
        if (btn) btn.classList.toggle('active', p === name);
      });
    }

    const params = new URLSearchParams(location.search);
    const pathMatch = location.pathname.match(/\/join\/(\d{5})/);
    const roomInput = document.getElementById('roomCodeInput');
    const nameInput = document.getElementById('deviceNameInput');
    const tokenInput = document.getElementById('tokenInput');
    const joinBtn = document.getElementById('joinBtn');
    const joinError = document.getElementById('joinError');
    const statusBadge = document.getElementById('statusBadge');
    const audioEl = document.getElementById('audioEl');
    const volumeSlider = document.getElementById('volumeSlider');
    const delaySlider = document.getElementById('delaySlider');
    const unlockBtn = document.getElementById('unlockAudioBtn');

    const initialCode = (pathMatch && pathMatch[1]) || params.get('room') || '';
    if (initialCode) {
      roomInput.value = initialCode;
      showPage('join');
    }
    if (params.get('token')) tokenInput.value = params.get('token');
    nameInput.value = localStorage.getItem('syncsound_web_name') || (navigator.platform + ' Browser');

    let ws = null;
    let deviceId = localStorage.getItem('syncsound_web_id') || ('web-' + Math.random().toString(36).slice(2, 10));
    localStorage.setItem('syncsound_web_id', deviceId);

    let samples = [];
    let clockOffsetMs = 0;
    let estimatedRttMs = 0;
    let pingSeq = 0;
    let currentSnapshot = null;
    let adminVolume = 1.0;
    let localVolume = 1.0;
    let isMuted = false;
    let allowLocalVol = true;
    let localDelayMs = 0;
    let scheduleTimer = null;
    let driftTimer = null;
    let pingTimer = null;

    function serverNow() { return Date.now() + clockOffsetMs; }
    function setBadge(text, cls) {
      statusBadge.textContent = text;
      statusBadge.className = 'badge ' + (cls || '');
    }

    unlockBtn.addEventListener('click', () => {
      audioEl.muted = false;
      audioEl.play().then(() => {
        if (!currentSnapshot || !currentSnapshot.isPlaying) audioEl.pause();
      }).catch(() => {});
      unlockBtn.classList.add('hidden');
      if (currentSnapshot && currentSnapshot.isPlaying) applySnapshot(currentSnapshot);
    });

    volumeSlider.addEventListener('input', () => {
      localVolume = Number(volumeSlider.value) / 100.0;
      document.getElementById('volPct').textContent = volumeSlider.value + '%';
      applyEffectiveVolume();
      if (ws && ws.readyState === 1) {
        ws.send(JSON.stringify({ type: 'local_volume', deviceId, volume: localVolume, isMuted }));
      }
    });

    delaySlider.addEventListener('input', () => {
      localDelayMs = Number(delaySlider.value);
      document.getElementById('delayLabel').textContent = localDelayMs + ' ms';
    });

    function applyEffectiveVolume() {
      const effective = isMuted ? 0 : Math.max(0, Math.min(1, (allowLocalVol ? localVolume : adminVolume) * (currentSnapshot ? currentSnapshot.masterVolume : 1)));
      audioEl.volume = effective;
    }

    joinBtn.addEventListener('click', connectWs);
    document.getElementById('leaveBtn').addEventListener('click', () => {
      if (ws) ws.close();
      document.getElementById('nav-speaker').classList.add('hidden');
      audioEl.pause();
      setBadge('DISCONNECTED', '');
      showPage('home');
    });

    function connectWs() {
      const code = roomInput.value.trim();
      const dName = nameInput.value.trim() || 'Web Speaker';
      const token = tokenInput.value.trim();
      if (code.length !== 5) { joinError.textContent = 'Enter a valid 5-digit room code.'; return; }
      localStorage.setItem('syncsound_web_name', dName);
      joinError.textContent = '';
      setBadge('CONNECTING', 'yellow');

      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
      ws = new WebSocket(proto + '//' + location.host + '/ws');
      ws.onopen = () => {
        const isMobile = /Android|iPad|iPhone/i.test(navigator.userAgent);
        ws.send(JSON.stringify({
          type: 'hello',
          deviceId,
          deviceName: dName,
          deviceType: isMobile ? 'WEB_BROWSER' : 'DESKTOP',
          roomCode: code,
          joinToken: token
        }));
      };
      ws.onmessage = (ev) => {
        const msg = JSON.parse(ev.data);
        if (msg.type === 'welcome') {
          if (!msg.accepted) {
            joinError.textContent = msg.reason || 'Join rejected';
            setBadge('REJECTED', 'red');
            ws.close();
            return;
          }
          deviceId = msg.assignedDeviceId || deviceId;
          document.getElementById('nav-speaker').classList.remove('hidden');
          document.getElementById('roomNameLabel').textContent = msg.roomInfo.roomName;
          document.getElementById('adminNameLabel').textContent = 'Admin: ' + msg.roomInfo.adminDeviceName;
          document.getElementById('roomCodeBadge').textContent = msg.roomInfo.roomCode;
          allowLocalVol = msg.settings ? msg.settings.allowLocalVolumeOverride : true;
          volumeSlider.disabled = !allowLocalVol;
          setBadge('SYNCING...', 'yellow');
          showPage('speaker');
          startClockPings();
          if (msg.snapshot) applySnapshot(msg.snapshot);
        } else if (msg.type === 'sync_pong') {
          const t4 = Date.now();
          const rtt = Math.max(0, (t4 - msg.t1ClientMs) - (msg.t3ServerSendMs - msg.t2ServerRecvMs));
          const offset = ((msg.t2ServerRecvMs - msg.t1ClientMs) + (msg.t3ServerSendMs - t4)) / 2;
          samples.push({ rtt, offset });
          if (samples.length > 10) samples.shift();
          const sorted = [...samples].sort((a, b) => a.rtt - b.rtt).slice(0, 5);
          clockOffsetMs = Math.round(sorted.reduce((s, x) => s + x.offset, 0) / sorted.length);
          estimatedRttMs = Math.round(sorted.reduce((s, x) => s + x.rtt, 0) / sorted.length);
          document.getElementById('rttVal').textContent = estimatedRttMs + ' ms';
          document.getElementById('offsetVal').textContent = clockOffsetMs + ' ms';
          setBadge('SYNCHRONIZED', 'green');
        } else if (msg.type === 'playback_cmd') {
          applySnapshot(msg.snapshot);
        } else if (msg.type === 'device_control') {
          if (msg.targetDeviceId === deviceId) {
            if (msg.kicked) {
              alert('Removed from room: ' + (msg.reason || 'Admin action'));
              location.href = '/';
              return;
            }
            if (msg.volume !== null && msg.volume !== undefined) {
              adminVolume = msg.volume;
              localVolume = msg.volume;
              volumeSlider.value = Math.round(msg.volume * 100);
              document.getElementById('volPct').textContent = Math.round(msg.volume * 100) + '%';
            }
            if (msg.isMuted !== null && msg.isMuted !== undefined) isMuted = msg.isMuted;
            if (msg.delayCalibrationMs !== null && msg.delayCalibrationMs !== undefined) {
              localDelayMs = msg.delayCalibrationMs;
              delaySlider.value = localDelayMs;
              document.getElementById('delayLabel').textContent = localDelayMs + ' ms';
            }
            applyEffectiveVolume();
          }
        } else if (msg.type === 'devices_update') {
          if (msg.settings) {
            allowLocalVol = msg.settings.allowLocalVolumeOverride;
            volumeSlider.disabled = !allowLocalVol;
          }
        } else if (msg.type === 'test_chime') {
          playTestChime(msg.chimeUrl, msg.scheduledServerTimeMs);
        } else if (msg.type === 'room_closed') {
          alert('Room closed: ' + msg.reason);
          location.href = '/';
        }
      };
      ws.onclose = () => {
        clearInterval(pingTimer);
        clearInterval(driftTimer);
        setBadge('DISCONNECTED', 'red');
      };
    }

    function startClockPings() {
      clearInterval(pingTimer);
      for (let i = 0; i < 5; i++) setTimeout(sendPing, i * 140);
      pingTimer = setInterval(sendPing, 3000);
    }

    function sendPing() {
      if (ws && ws.readyState === 1) {
        ws.send(JSON.stringify({ type: 'sync_ping', seq: ++pingSeq, t1ClientMs: Date.now() }));
      }
    }

    function playTestChime(chimeUrl, scheduledServerTimeMs) {
      const delay = Math.max(0, scheduledServerTimeMs - serverNow());
      audioEl.src = chimeUrl;
      audioEl.load();
      setTimeout(() => { audioEl.currentTime = 0; audioEl.play().catch(() => {}); }, delay);
    }

    function applySnapshot(snap) {
      currentSnapshot = snap;
      clearTimeout(scheduleTimer);
      clearInterval(driftTimer);
      const track = snap.track;
      if (!track) {
        document.getElementById('trackTitle').textContent = 'Queue Empty';
        document.getElementById('trackArtist').textContent = 'Waiting for Admin';
        audioEl.pause();
        return;
      }
      document.getElementById('sourceTag').textContent = track.sourceType;
      document.getElementById('trackTitle').textContent = track.title;
      document.getElementById('trackArtist').textContent = track.artist;
      applyEffectiveVolume();

      if (track.sourceType === 'YOUTUBE') {
        audioEl.pause();
        document.getElementById('ytWrap').classList.remove('hidden');
        const embedUrl = 'https://www.youtube.com/embed/' + encodeURIComponent(track.externalId) +
          '?autoplay=' + (snap.isPlaying ? '1' : '0') + '&start=' + Math.floor(snap.startPositionMs / 1000);
        document.getElementById('ytPlayer').innerHTML = '<iframe width="100%" height="100%" src="' + embedUrl + '" frameborder="0" allow="autoplay; encrypted-media" allowfullscreen></iframe>';
        return;
      } else {
        document.getElementById('ytWrap').classList.add('hidden');
      }

      if (!track.streamUrl) return;
      const resolvedUrl = track.streamUrl.startsWith('/') ? (location.origin + track.streamUrl) : track.streamUrl;
      if (audioEl.src !== resolvedUrl) {
        audioEl.src = resolvedUrl;
        audioEl.load();
      }
      if (!snap.isPlaying) {
        audioEl.pause();
        audioEl.currentTime = Math.max(0, snap.startPositionMs / 1000);
        document.getElementById('driftVal').textContent = '0 ms';
        return;
      }

      const delayMs = Math.max(0, snap.scheduledServerTimeMs - serverNow());
      const startSec = Math.max(0, (snap.startPositionMs + (delayMs === 0 ? (serverNow() - snap.scheduledServerTimeMs) : 0) + localDelayMs) / 1000);
      audioEl.currentTime = startSec;

      scheduleTimer = setTimeout(() => {
        audioEl.playbackRate = 1.0;
        audioEl.play().then(() => unlockBtn.classList.add('hidden')).catch(() => unlockBtn.classList.remove('hidden'));
        startDriftMonitor();
      }, delayMs);
    }

    function startDriftMonitor() {
      clearInterval(driftTimer);
      driftTimer = setInterval(() => {
        if (!currentSnapshot || !currentSnapshot.isPlaying) return;
        const expectedMs = currentSnapshot.startPositionMs + (serverNow() - currentSnapshot.scheduledServerTimeMs) + localDelayMs;
        const actualMs = audioEl.currentTime * 1000;
        const driftMs = Math.round(expectedMs - actualMs);
        document.getElementById('driftVal').textContent = driftMs + ' ms';

        if (Math.abs(driftMs) <= 45) {
          if (audioEl.playbackRate !== 1.0) audioEl.playbackRate = 1.0;
          setBadge('SYNCHRONIZED', 'green');
        } else if (Math.abs(driftMs) <= 200) {
          audioEl.playbackRate = driftMs > 0 ? 1.035 : 0.965;
          setBadge('DRIFT ADJUST', 'yellow');
        } else {
          audioEl.playbackRate = 1.0;
          audioEl.currentTime = Math.max(0, expectedMs / 1000);
          setBadge('RESYNCING', 'yellow');
        }

        if (ws && ws.readyState === 1) {
          ws.send(JSON.stringify({
            type: 'telemetry',
            deviceId,
            positionMs: Math.round(actualMs),
            driftMs,
            rttMs: estimatedRttMs,
            clockOffsetMs,
            isPlaying: !audioEl.paused,
            localVolume,
            isMuted
          }));
        }
      }, 1000);
    }
  </script>
</body>
</html>
    """.trimIndent()

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}

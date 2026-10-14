// Boot: loading progress → init → menu.
import { Game } from './game.js';
import { SaveSystem } from './save.js';

const fill = document.getElementById('load-fill');
const text = document.getElementById('load-text');
function progress(p, msg) {
  fill.style.width = Math.round(p * 100) + '%';
  if (msg) text.textContent = msg;
}

const game = new Game();
window.__game = game; // debuggable / test hook

try {
  progress(0.02, 'Waking the renderer...');
  await game.init(progress);
  game.start();
  progress(1, 'Ready');
  document.getElementById('loading-screen').classList.add('hidden');
  document.getElementById('main-menu').classList.remove('hidden');
  document.getElementById('btn-continue').style.opacity = SaveSystem.has() ? '1' : '0.45';
  // ?auto=new query for automated capture: skip menu into game
  const q = new URLSearchParams(location.search);
  if (q.get('auto') === 'new') {
    document.getElementById('main-menu').classList.add('hidden');
    game.startFresh();
  }
  // first user gesture unlocks audio on mobile
  const unlock = () => { import('./audio.js').then(({ audio }) => { audio.resume(); }); };
  window.addEventListener('touchstart', unlock, { once: true });
  window.addEventListener('mousedown', unlock, { once: true });
} catch (err) {
  console.error(err);
  text.textContent = 'Failed to start: ' + (err && err.message);
  text.style.color = '#ff8a8a';
}

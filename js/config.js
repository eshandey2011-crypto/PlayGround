// Central balance / content configuration. Single source of truth for tuning.
export const CONFIG = {
  worldSize: 420,           // playable square (m)
  killY: -30,
  player: {
    walkSpeed: 6, runSpeed: 10.5, jumpVel: 8.2, dodgeSpeed: 13, dodgeTime: 0.42, dodgeCost: 22,
    jumpCost: 8, lightCost: 9, heavyCost: 18,
    baseHP: 100, baseST: 100, baseEN: 100,
    hpPerLevel: 14, stPerLevel: 6, enPerLevel: 8,
    lightDmg: [10, 11, 14], heavyDmg: 26, special1Dmg: 45, special2Dmg: 70,
    special1Cost: 35, special2Cost: 60,
    xpCurve: (lvl) => Math.floor(60 * Math.pow(lvl, 1.55)),
    staminaRegen: 22, energyRegen: 10, hpRegen: 1.2,
    blockMitigation: 0.72, dodgeIframes: 0.38,
  },
  difficulties: {
    story:  { enemyHP: 0.65, enemyDmg: 0.55, playerDmg: 1.35, xp: 1.3,  label: 'Story' },
    normal: { enemyHP: 1.0,  enemyDmg: 1.0,  playerDmg: 1.0,  xp: 1.0,  label: 'Normal' },
    hard:   { enemyHP: 1.45, enemyDmg: 1.5,  playerDmg: 0.85, xp: 1.15, label: 'Hard' },
  },
  graphics: {
    low:    { pixelRatio: 0.6,  shadows: false, shadowSize: 512,  trees: 90,  grass: 0,    rain: 400,  viewDist: 150, particles: 0.4 },
    medium: { pixelRatio: 0.85, shadows: true,  shadowSize: 1024, trees: 170, grass: 800,  rain: 1200, viewDist: 220, particles: 0.7 },
    high:   { pixelRatio: 1.0,  shadows: true,  shadowSize: 2048, trees: 260, grass: 2200, rain: 2500, viewDist: 300, particles: 1.0 },
    ultra:  { pixelRatio: 1.5,  shadows: true,  shadowSize: 2048, trees: 340, grass: 4000, rain: 4000, viewDist: 380, particles: 1.3 },
  },
  enemies: {
    wolf:   { hp: 45,  dmg: 9,  speed: 7.5, xp: 22,  aggro: 22, attackRange: 2.2, atkCd: 1.3, behavior: 'chaser',  name: 'Shadow Wolf', color: 0x3a3f4d },
    scout:  { hp: 70,  dmg: 11, speed: 5.2, xp: 34,  aggro: 26, attackRange: 2.4, atkCd: 1.5, behavior: 'circler', name: 'Bandit Scout', color: 0x6b4a2f },
    brute:  { hp: 150, dmg: 20, speed: 3.8, xp: 65,  aggro: 24, attackRange: 2.8, atkCd: 2.1, behavior: 'brute',   name: 'Camp Brute', color: 0x5d2f2f },
    shaman: { hp: 90,  dmg: 14, speed: 4.2, xp: 80,  aggro: 34, attackRange: 20,  atkCd: 2.6, behavior: 'ranged',  name: 'Hex Shaman', color: 0x4a2f6b },
    boss:   { hp: 950, dmg: 26, speed: 5.6, xp: 600, aggro: 999, attackRange: 3.4, atkCd: 1.4, behavior: 'boss',   name: 'Shadow Warlord', color: 0x12060a },
  },
  weapons: [
    { id: 'rusty',   name: 'Worn Blade',      dmgMul: 1.0,  desc: '+0% damage. A soldier\'s old sword.' },
    { id: 'iron',    name: 'Iron Fang',       dmgMul: 1.25, desc: '+25% damage. Found in the bandit camp.', price: 0 },
    { id: 'rune',    name: 'Runebrand',       dmgMul: 1.55, desc: '+55% damage. Hums near the temple.', price: 250 },
    { id: 'legend',  name: 'Dawnbreaker',     dmgMul: 2.0,  desc: '+100% damage. Legend made steel.', price: 600 },
  ],
  skills: [
    { id: 'sunder',  name: 'Sunder Strike ✦', cost: 35, dmg: 45, cd: 6,  unlock: 2, desc: 'A 360° shockwave. Unlocks at Lv 2.' },
    { id: 'wrath',   name: 'Heaven\'s Wrath ❖', cost: 60, dmg: 70, cd: 14, unlock: 4, desc: 'Call lightning on locked/nearest foe. Unlocks at Lv 4.' },
    { id: 'ironwill',name: 'Iron Will', passive: true, unlock: 3, desc: 'Lv 3: +25 max HP, blocking costs no stamina.' },
    { id: 'windfoot',name: 'Wind Foot', passive: true, unlock: 5, desc: 'Lv 5: +15% move speed, dodge costs -40%.' },
    { id: 'vampiric',name: 'Vampiric Edge', passive: true, unlock: 6, desc: 'Lv 6: heal 6 HP per combo finisher.' },
  ],
  items: {
    potion:   { name: 'Health Potion',  heal: 55, price: 30, desc: 'Restores 55 HP.' },
    elixir:   { name: 'Stamina Elixir', sten: 60, price: 22, desc: 'Restores 60 stamina.' },
    focus:    { name: 'Focus Charm',    en: 60,   price: 25, desc: 'Restores 60 energy.' },
    bomb:     { name: 'Fire Bomb',      dmg: 60,  price: 45, desc: 'Throw: 60 damage in 5m.' },
  },
  dayLength: 360, // seconds per full day
};

export const QUESTS = [
  { id: 'q1', kind: 'main', name: 'Ashes of Dawn', obj: 'Speak with Elder Maren in the village',
    hint: 'Follow the golden beacon to the village.', beacon: 'village' },
  { id: 'q2', kind: 'main', name: 'The Bandit Toll', obj: 'Clear the bandit camp (0/4) east of the village',
    hint: 'Defeat 4 camp enemies. Dodge (Space) through heavy swings.', beacon: 'camp', need: { kills: 4, zone: 'camp' } },
  { id: 'q3', kind: 'main', name: 'Whispers in the Cave', obj: 'Enter the cave and claim the Temple Key',
    hint: 'The cave mouth glows blue, north-west. Beware wolves.', beacon: 'cave' },
  { id: 'q4', kind: 'main', name: 'The Sunken Temple', obj: 'Unlock the temple and defeat the Guardian Brute',
    hint: 'Use the key at the temple seal, south. Block (hold L) its slam.', beacon: 'temple' },
  { id: 'q5', kind: 'main', name: 'Rise of the Legend', obj: 'Slay the SHADOW WARLORD atop the mountain',
    hint: 'Climb to the summit arena. Save your Wrath for phase 2.', beacon: 'summit' },
  { id: 's1', kind: 'side', name: 'Lost Heirloom', obj: 'Find the heirloom chest in the forest ruins',
    hint: 'A mossy chest hides among the western woods.', beacon: 'heirloom' },
  { id: 's2', kind: 'side', name: 'Wolf Cull', obj: 'Cull shadow wolves (0/5) anywhere',
    hint: 'Wolves roam the wilds at night especially.', beacon: null, need: { kills: 5, type: 'wolf' } },
  { id: 's3', kind: 'side', name: 'Herbs for the Healer', obj: 'Gather 3 moonherbs (glowing flowers)',
    hint: 'Moonherbs glow cyan near water and the cave.', beacon: null, need: { herbs: 3 } },
];

export const DIALOGUES = {
  elder0: [
    { who: 'Elder Maren', text: 'Warrior... our valley burns. Bandits tax our roads, and the old temple stirs with shadow.' },
    { who: 'You', text: 'Then point me at the fire, Elder. I did not climb down from the pass to watch.' },
    { who: 'Elder Maren', text: 'Ha! Spirit. First — clear their camp east of here. Take my potions. Come back alive, and we\'ll speak of the cave.' },
  ],
  elder1: [
    { who: 'Elder Maren', text: 'The camp burns — I saw the smoke. You fight like the old legends.' },
    { who: 'Elder Maren', text: 'Listen: the cave north-west holds a Temple Key. The temple seal will only open for its bearer. Go with dawn.' },
  ],
  healer: [
    { who: 'Healer Sera', text: 'Moonherbs, warrior — three glowing blooms. Bring them and I\'ll make you tonics that knit bone.' },
  ],
  smith: [
    { who: 'Smith Dain', text: 'Blade\'s dull? Gold spends. I sell runed steel — and I\'ll buy nothing but your victories.' },
  ],
};

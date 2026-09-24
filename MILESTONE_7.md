# Milestone 7 — Visual Identity, Emblems, Themes

Version: **0.7.0-m7**

This milestone turns the functional journal shell into the intended **Tome of Healing** visual system while keeping the writing surface practical.

## Implemented

### Layered emblem maker
Fields and Topics can now edit a persistent layered emblem composed of:

- Base: open tome, shield, circle/medallion, hex, rune plate
- Central medical/fantasy symbol: Rod of Asclepius, heart, brain, eye, lungs, bone, flask, cross, skull
- Secondary motif: arcane halo, laurel, wings, none
- Frame: silver filigree, rune metal, plain, none
- Primary / metal / accent color layers
- Adjustable glow
- Optional banner and initials

Subtopics are still created from the parent's current emblem, preserving the previously selected parent-derived inheritance model. The editor also offers **Reset to parent emblem**.

### Tome of Healing crest
The app-level crest is now defined directly in vector drawing code:

- Open tome base
- Rod of Asclepius
- Arcane rune halo
- Silver filigree frame
- Deep Purple / Moon Silver / Deep Blue
- Restrained animated ambient glow

The crest is used on the launch experience and library identity.

### Fantasy-modern visual system
The UI now has:

- High-fantasy serif display/title typography with clean sans-serif utility/body typography
- Dark fantasy backdrop with material-dependent subtle texture
- Silver/rune dividers
- Layered fantasy panels instead of plain Material cards
- Emblems on Fields, Topics, Subtopics and Note rows
- Pinned/master content receives a restrained emphasized border/glow

### Theme Workshop
The root library includes a Theme editor with presets:

- Moonlit Archive (default)
- Emerald Healer
- Crimson Citadel
- Arcane Violet
- Ancient Parchment

Advanced customization supports:

- Primary / secondary / accent ARGB colors
- Background and panel colors
- Material: moon stone, dark iron, aged wood, arcane glass, parchment
- Texture strength
- Ambient glow strength
- Animation intensity

Theme choices persist through Android SharedPreferences and apply without restarting the app.

### Switchable library layouts
The selected library presentation is now persistent and switchable between:

- Grid
- List
- Tome
- Command Panel

This fulfills the earlier layout-selection requirement without changing journal hierarchy or data.

### Launch experience
A 1.5-second launch screen now shows the animated Tome of Healing crest and opens directly into the Field Library.

### App icon
The Android launcher now uses a vector Tome/Asclepius icon derived from the main crest palette.

## Physical-device checks

1. Launch the app and confirm the crest intro lasts roughly 1–2 seconds.
2. On the Field Library, open **Theme** and switch among all presets.
3. Edit individual ARGB values and verify the live app shell changes after Apply.
4. Open **View** and test Grid, List, Tome, and Command Panel modes in portrait and landscape.
5. Long-press a Field → **Edit emblem**. Change all layer categories, colors, glow, banner and initials.
6. Create a Topic beneath that Field and verify it starts with the Field emblem.
7. Edit the Topic emblem; create a Subtopic and verify the Subtopic starts from the edited Topic emblem.
8. In a Topic, verify every Note row uses the Topic emblem and Master Summary has the emphasized treatment.
9. Open a handwritten note and confirm the fantasy top bar/background does not alter pen/finger/stylus behavior.
10. Force-close and reopen. Verify custom emblems, theme choice, and library layout remain.

## Notes

No copyrighted Warcraft III / Age of the Ring art assets are included. The implementation uses original vector primitives and a general dark high-fantasy RTS visual language.

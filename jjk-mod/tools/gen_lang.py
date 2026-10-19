"""Generates assets/jjk/lang/en_us.json. Keep keys in sync with the Java enums / Abilities registry."""
import json, sys

L = {}
L["itemGroup.jjk"] = "Jujutsu Kaisen"
L["key.categories.jjk"] = "Jujutsu Kaisen"
L["key.jjk.activate"] = "Activate Ability"
L["key.jjk.cycle"] = "Cycle Ability (Shift: back)"
L["key.jjk.domain"] = "Domain Expansion"
L["key.jjk.rct"] = "Reverse Cursed Technique"

# Items
L["item.jjk.jjk_emblem"] = "Jujutsu High Emblem"
L["item.jjk.cursed_energy_scroll"] = "Cursed Energy Scroll"
L["item.jjk.clan_scroll"] = "Cursed Clan Scroll"
L["item.jjk.heavenly_restriction_scroll"] = "Heavenly Restriction Scroll"
L["item.jjk.cursed_energy_scroll.tooltip"] = "Unseals your Innate Technique and the depth of your cursed energy."
L["item.jjk.clan_scroll.tooltip"] = "Reveals the bloodline you were born into."
L["item.jjk.heavenly_restriction_scroll.tooltip"] = "A binding vow struck at birth. Very rarely, it takes."
L["item.jjk.scroll.tooltip.use"] = "Right-click to unroll"

# Rarity / grade / tiers / natures
for k, v in {"common": "Common", "rare": "Rare", "epic": "Epic", "legendary": "Legendary", "special_grade": "Special Grade"}.items():
    L[f"jjk.rarity.{k}"] = v
for k, v in {"grade_4": "Grade 4", "grade_3": "Grade 3", "grade_2": "Grade 2", "semi_grade_1": "Semi-Grade 1",
             "grade_1": "Grade 1", "special_grade": "Special Grade"}.items():
    L[f"jjk.grade.{k}"] = v
for k, v in {"low": "Low", "average": "Average", "high": "High", "massive": "Massive", "monstrous": "Monstrous"}.items():
    L[f"jjk.ce_tier.{k}"] = v
for k, v in {"standard": "Standard", "sharp": "Sharp", "dense": "Dense", "flowing": "Flowing",
             "volatile": "Volatile", "pristine": "Pristine"}.items():
    L[f"jjk.ce_nature.{k}"] = v

# Techniques
T = {
    "none": ("No Technique", "You have cursed energy, but no innate technique to channel it."),
    "limitless": ("Limitless", "Bring infinity into reality. Infinity, Blue, Red and Hollow Purple. Domain: Unlimited Void."),
    "shrine": ("Shrine", "The King of Curses' technique. Dismantle, Cleave and Fuga. Domain: Malevolent Shrine."),
    "ten_shadows": ("Ten Shadows", "Summon shikigami from your shadow, up to the Eight-Handled Sword Divergent Sila Divine General Mahoraga. Domain: Chimera Shadow Garden."),
    "idle_transfiguration": ("Idle Transfiguration", "Touch the soul and reshape the body. Domain: Self-Embodiment of Perfection."),
    "disaster_flames": ("Disaster Flames", "Volcanic cursed flames: Ember Insects and Maximum: Meteor. Domain: Coffin of the Iron Mountain."),
    "cursed_spirit_manipulation": ("Cursed Spirit Manipulation", "Absorb defeated curses and release them as your own. Culminates in Uzumaki."),
    "cursed_speech": ("Cursed Speech", "Words become commands: Don't Move, Blast Away, Crush. Strains the throat."),
    "blood_manipulation": ("Blood Manipulation", "The Kamo clan's art: Piercing Blood, Supernova and Flowing Red Scale convergence."),
    "projection_sorcery": ("Projection Sorcery", "Move in 24 frames per second. Anyone who breaks the rules freezes in a frame."),
    "boogie_woogie": ("Boogie Woogie", "Clap to swap places with anything that has cursed energy."),
    "ratio": ("Ratio Technique", "Force a weak point at the 7:3 line of any target. Overtime strengthens you."),
    "straw_doll": ("Straw Doll Technique", "Nails and a hammer: Resonance strikes from afar, Hairpin detonates."),
    "black_flash_affinity": ("Black Flash Affinity", "No technique to speak of, but the sparks of black flash come to you naturally."),
    "puppet_manipulation": ("Puppet Manipulation", "Fight through cursed corpses and mechanized puppets. Ultra Cannon!"),
    "construction": ("Construction", "Create matter from cursed energy. Exhausting, but versatile."),
    "comedian": ("Comedian", "If you find it funny, it happens. Reality bends to the punchline."),
    "ce_reinforcement": ("Cursed Energy Reinforcement", "Raw reinforcement of the body with cursed energy. Simple and reliable."),
}
for k, (n, d) in T.items():
    L[f"jjk.technique.{k}"] = n
    L[f"jjk.technique.{k}.desc"] = d

C = {
    "clanless": ("Clanless", "Commoner blood. No inheritance, but a small bonus to everything (+5%)."),
    "fujiwara": ("Fujiwara Clan", "A Heian-era lineage. +20% CE regeneration; leans toward Straw Doll, Ratio and Disaster Flames."),
    "zenin": ("Zen'in Clan", "+25% weapon damage. Strong chance of Ten Shadows or Projection Sorcery."),
    "kamo": ("Kamo Clan", "+10% max CE. Very likely to inherit Blood Manipulation."),
    "inumaki": ("Inumaki Clan", "The snake-and-fangs crest. Almost always inherits Cursed Speech."),
    "gojo": ("Gojo Clan", "Six Eyes: -30% CE costs, +15% regen and you perceive others' cursed energy. Favors Limitless."),
    "ryomen": ("Ryomen Bloodline", "The blood of the King of Curses. +10% max CE, +15% technique damage, strongly favors Shrine."),
}
for k, (n, d) in C.items():
    L[f"jjk.clan.{k}"] = n
    L[f"jjk.clan.{k}.desc"] = d

H = {
    "none": ("No Restriction", "The vow found nothing to bind. Your body stays as it was."),
    "physical": ("Heavenly Restriction: Physical", "Zero cursed energy and no technique, in exchange for monstrous strength, speed, durability and senses. Curses cannot sense you, and you can see them without glasses."),
    "cursed_energy": ("Heavenly Restriction: Cursed Energy", "A frail body, in exchange for vast cursed energy, faster regeneration and far longer range."),
}
for k, (n, d) in H.items():
    L[f"jjk.heavenly_restriction.{k}"] = n
    L[f"jjk.heavenly_restriction.{k}.desc"] = d

A = {
    "cursed_strike": "Cursed Strike", "ce_reinforcement": "CE Reinforcement", "heavenly_dash": "Heavenly Dash",
    "ground_slam": "Ground Slam", "infinity": "Infinity", "blue": "Lapse: Blue", "red": "Reversal: Red",
    "hollow_purple": "Hollow Purple", "dismantle": "Dismantle", "cleave": "Cleave", "fuga": "Fuga",
    "divine_dogs": "Divine Dogs", "nue": "Nue", "toad": "Toad", "max_elephant": "Max Elephant",
    "rabbit_escape": "Rabbit Escape", "mahoraga": "Mahoraga", "soul_touch": "Soul Touch",
    "polymorphic_soul_isomer": "Polymorphic Soul Isomer", "body_repel": "Body Repel",
    "ember_insects": "Ember Insects", "flame_burst": "Flame Burst", "maximum_meteor": "Maximum: Meteor",
    "absorb_curse": "Absorb Curse", "release_curse": "Release Curse", "uzumaki": "Uzumaki",
    "dont_move": "\"Don't Move\"", "blast_away": "\"Blast Away\"", "crush": "\"Crush\"",
    "piercing_blood": "Piercing Blood", "supernova": "Supernova", "convergence": "Flowing Red Scale",
    "projection_dash": "24 FPS Dash", "frame_freeze": "Frame Freeze", "swap": "Boogie Woogie",
    "ratio_strike": "7:3 Strike", "overtime": "Overtime", "resonance": "Resonance", "hairpin": "Hairpin",
    "divergent_fist": "Divergent Fist", "focus": "Focus", "ultra_cannon": "Ultra Cannon",
    "puppet_guard": "Puppet Guard", "construct_blade": "Construct Blade", "construct_wall": "Construct Wall",
    "punchline": "Punchline", "reinforce": "Reinforce", "ce_burst": "Cursed Energy Burst",
    "domain_expansion": "Domain Expansion", "reverse_cursed_technique": "Reverse Cursed Technique",
}
for k, v in A.items():
    L[f"jjk.ability.{k}"] = v

D = {
    "unlimited_void": "Unlimited Void", "malevolent_shrine": "Malevolent Shrine",
    "chimera_shadow_garden": "Chimera Shadow Garden", "self_embodiment_of_perfection": "Self-Embodiment of Perfection",
    "coffin_of_the_iron_mountain": "Coffin of the Iron Mountain", "incomplete_domain": "Incomplete Domain",
}
for k, v in D.items():
    L[f"jjk.domain.{k}"] = v

# Messages / UI
L.update({
    "jjk.message.no_ability": "You have no ability to use.",
    "jjk.message.cooldown": "%s is on cooldown (%ss)",
    "jjk.message.not_enough_ce": "Not enough cursed energy!",
    "jjk.message.rct_locked": "You have not learned Reverse Cursed Technique.",
    "jjk.message.domain_locked": "Your domain has not yet taken shape. (Reach Grade 1)",
    "jjk.message.domain_cast": "%s: Domain Expansion — %s!",
    "jjk.message.restriction_rejects_scroll": "Your Heavenly Restriction rejects cursed techniques.",
    "jjk.message.no_scrolls": "You have no more scrolls of that kind.",
    "jjk.message.roll_accepted": "The scroll's vow is sealed: %s",
    "jjk.message.grade_up": "Promotion! %s → %s",
    "jjk.message.welcome": "A sorcerer's path begins. Unroll your scrolls to awaken your technique and bloodline.",
    "jjk.roll.title.cursed_energy": "Cursed Energy Scroll",
    "jjk.roll.title.clan": "Cursed Clan Scroll",
    "jjk.roll.title.heavenly_restriction": "Heavenly Restriction Scroll",
    "jjk.roll.accept": "Accept",
    "jjk.roll.reroll": "Reroll (%s left)",
    "jjk.roll.discard": "Discard",
    "jjk.roll.spinning": "The seal is unwinding...",
    "jjk.roll.ce_line": "Cursed Energy: %s  ·  Nature: %s",
    "jjk.hud.heavenly_restriction": "HEAVENLY RESTRICTION",
    "jjk.command.reset": "Jujutsu data reset.",
    "jjk.command.info": "%s — %s | Technique: %s | Clan: %s | CE: %s (%s) | Restriction: %s | CE %s, regen %s/s | XP %s, curses exorcised %s | Abilities: %s",
    "death.attack.jjk.cursed_technique": "%1$s was exorcised by %2$s",
    "death.attack.jjk.cursed_technique.item": "%1$s was exorcised by %2$s using %3$s",
    "death.attack.jjk.domain_sure_hit": "%1$s could not escape %2$s's domain",
    "death.attack.jjk.domain_sure_hit.item": "%1$s could not escape %2$s's domain",
})

# Sound subtitles
for k, v in {"roll.open": "Scroll unrolls", "roll.tick": "Reel ticks", "roll.reveal": "Roll revealed",
             "roll.reveal_rare": "Rare roll revealed", "ability.cast": "Technique cast", "ability.impact": "Cursed impact",
             "ability.dash": "Sorcerer dashes", "ability.clap": "Hands clap", "ability.heal": "Reverse cursed energy",
             "ce.depleted": "Cursed energy depleted", "grade.up": "Grade promotion", "domain.expansion": "Domain Expansion"}.items():
    L[f"subtitles.jjk.{k}"] = v

# Advancements
for k, (t, d) in {
    "root": ("Jujutsu Kaisen", "Unroll your first scroll"),
    "special_grade": ("Special Grade", "Reach Special Grade as a sorcerer"),
}.items():
    L[f"advancements.jjk.{k}.title"] = t
    L[f"advancements.jjk.{k}.description"] = d

with open(sys.argv[1], "w", encoding="utf-8") as f:
    json.dump(dict(sorted(L.items())), f, indent=2, ensure_ascii=False)
print(len(L), "keys")

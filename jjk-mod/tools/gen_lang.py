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

# ================================================================ Phase 2: items
ITEMS = {
    "cursed_fragment": ("Cursed Fragment", "A shard of a curse's body. Used to craft talismans and tools."),
    "ce_shard": ("Cursed Energy Shard", "Crystallized cursed energy. Eat to restore 20 CE."),
    "sukuna_finger": ("Sukuna's Finger", "One of twenty. A special grade cursed object. Do not eat."),
    "death_painting": ("Cursed Womb: Death Painting", "One of the nine Death Paintings. Absorb it for cursed energy."),
    "special_grade_object": ("Special Grade Cursed Object", "Sealed and wrapped in talismans. Absorbable, or a crafting core."),
    "ce_bottle": ("Bottled Cursed Energy", None),
    "talisman": ("Ward Talisman", "Carry it to block a single cursed technique hit."),
    "prison_realm": ("Prison Realm", "Right-click a creature to seal it away for 30s (bosses: 12s)."),
    "student_id": ("Jujutsu High Student ID", "Right-click to register it. Sorcerers only trade with registered students."),
    "curse_glasses": ("Curse-Sight Glasses", None),
    "inverted_spear_of_heaven": ("Inverted Spear of Heaven", None),
    "playful_cloud": ("Playful Cloud", None),
    "split_soul_katana": ("Split Soul Katana", None),
    "dragon_bone": ("Dragon Bone", None),
    "slaughter_demon": ("Slaughter Demon", None),
    "chain_of_a_thousand_miles": ("Chain of a Thousand Miles", None),
    "black_rope": ("Black Rope", None),
}
for k, (n, t) in ITEMS.items():
    L[f"item.jjk.{k}"] = n
    if t: L[f"item.jjk.{k}.tooltip"] = t
L.update({
    "item.jjk.ce_bottle.tooltip": "Restores %s%% of your max cursed energy.",
    "item.jjk.curse_glasses.tooltip": "Lets anyone see cursed spirits.",
    "item.jjk.inverted_spear_of_heaven.tooltip": "Nullifies any cursed technique it touches.",
    "item.jjk.playful_cloud.tooltip": "Three-section staff. Pure physical force, no technique.",
    "item.jjk.split_soul_katana.tooltip": "Cuts the soul directly; armor is no defense.",
    "item.jjk.dragon_bone.tooltip": "Stores impact you take and releases it on your next hit.",
    "item.jjk.dragon_bone.stored": "Stored: %s damage",
    "item.jjk.slaughter_demon.tooltip": "Deals heavy bonus damage to curses.",
    "item.jjk.chain_of_a_thousand_miles.tooltip": "Extends your reach by 5 blocks.",
    "item.jjk.black_rope.tooltip": "Unravels techniques and strips the target's buffs.",
    "item.jjk.student_id.owner": "Registered to %s",
    "item.jjk.ce_shard.absorbed": "Cursed energy surges back.",
    "item.jjk.death_painting.absorbed": "The Death Painting's blood becomes yours.",
    "item.jjk.special_grade_object.absorbed": "You absorb the cursed object.",
    "effect.jjk.exiled": "Exiled",
    "effect.jjk.sealed": "Sealed",
    "effect.jjk.nullified": "Nullified",
    "jjk.message.finger_eaten": "You devour a finger... (%s / %s)",
    "jjk.message.finger_rejected": "Your body has no cursed energy to hold it. It burns.",
    "jjk.message.takeover_start": "\"Know your place, fool.\" Sukuna has taken over!",
    "jjk.message.takeover_end": "Sukuna recedes. You are yourself again... for now.",
    "jjk.message.prison_realm": "Prison Realm: %s is sealed.",
    "jjk.message.prison_realm_sealed": "You have been sealed inside the Prison Realm!",
    "jjk.message.suppressed": "Your cursed technique won't respond!",
    "jjk.message.student_id_registered": "Student ID registered to you.",
    "jjk.message.student_id_info": "Tokyo Jujutsu High — %s, curses exorcised: %s",
})
for k, v in {"item.finger_eat": "Finger devoured", "item.sukuna_laugh": "Sukuna laughs", "item.prison_realm": "Prison Realm opens",
             "item.talisman_burn": "Talisman burns"}.items():
    L[f"subtitles.jjk.{k}"] = v
for k, (t, d) in {
    "first_finger": ("Cursed Object", "Eat one of Sukuna's fingers"),
    "vessel": ("Vessel", "Let the King of Curses take over your body"),
    "ten_fingers": ("Half the King", "Eat 10 of Sukuna's fingers"),
    "all_fingers": ("The Honored One", "Eat all 20 of Sukuna's fingers"),
    "cursed_tool": ("Tools of the Trade", "Obtain a cursed tool"),
}.items():
    L[f"advancements.jjk.{k}.title"] = t
    L[f"advancements.jjk.{k}.description"] = d

# ================================================================ Phase 3: abilities, VFX, domains
for k, v in A.items():
    L[f"subtitles.jjk.technique.{k}"] = v
for k, v in D.items():
    L[f"subtitles.jjk.domain.{k}"] = f"Domain Expansion: {v}"
L.update({
    "jjk.ability.simple_domain": "Simple Domain",
    "subtitles.jjk.technique.simple_domain": "Simple Domain",
    "jjk.ability.domain_amplification": "Domain Amplification",
    "subtitles.jjk.technique.domain_amplification": "Domain Amplification",
    "subtitles.jjk.domain.shatter": "Domain shatters",
    "subtitles.jjk.black_flash": "Black Flash",
    "effect.jjk.simple_domain": "Simple Domain",
    "effect.jjk.domain_amplification": "Domain Amplification",
    "effect.jjk.infinity": "Infinity",
    "block.jjk.domain_barrier": "Domain Barrier",
    "block.jjk.domain_floor": "Domain Floor",
    "entity.jjk.technique_projectile": "Cursed Technique",
    "jjk.fx.black_flash": "BLACK FLASH",
    "jjk.fx.domain_expansion": "Domain Expansion",
    "jjk.fx.domain_clash": "DOMAIN CLASH",
    "jjk.message.domain_clash_lost": "Your domain was overwritten by a stronger one!",
    "jjk.message.domain_clash_won": "Your domain overwhelms theirs!",
    "jjk.message.burnout": "Your technique has burnt out...",
    "jjk.message.infinity_down": "Infinity collapses: out of cursed energy.",
    "jjk.message.amplification_blocks": "You can't use your technique while amplifying your domain.",
})
for k, (t, d) in {
    "first_domain": ("Domain Expansion", "Expand your domain for the first time"),
    "domain_clash": ("Domain Clash", "Collide your domain with another sorcerer's"),
    "black_flash": ("Black Flash", "Land a Black Flash"),
}.items():
    L[f"advancements.jjk.{k}.title"] = t
    L[f"advancements.jjk.{k}.description"] = d

# ================================================================ Phase 4: curses and shikigami
CURSES = {"fly_head": "Fly Head", "roppongi_curse": "Roppongi Curse", "finger_bearer": "Finger Bearer",
          "rainbow_dragon": "Rainbow Dragon", "smallpox_deity": "Smallpox Deity"}
for k, v in CURSES.items():
    L[f"entity.jjk.{k}"] = v
    L[f"item.jjk.{k}_spawn_egg"] = f"{v} Spawn Egg"
    for kind, word in (("ambient", "groans"), ("hurt", "hurts"), ("death", "is exorcised")):
        L[f"subtitles.jjk.curse.{k}.{kind}"] = f"{v} {word}"
for k, v in {"divine_dog": "Divine Dog", "nue": "Nue", "toad": "Toad", "max_elephant": "Max Elephant", "shadow_rabbit": "Rabbit Escape"}.items():
    L[f"entity.jjk.{k}"] = v
L.update({
    "jjk.message.absorb_no_target": "Look at a cursed spirit to absorb it.",
    "jjk.message.absorb_too_strong": "That curse is too strong. Weaken it first!",
    "jjk.message.absorb_full": "You can't hold any more curses.",
    "jjk.message.absorbed": "Curse absorbed (%s / %s).",
})

# ================================================================ Phase 5: outfits
for k, v in {
    "jujutsu_uniform_jacket": "Jujutsu High Uniform Jacket", "jujutsu_uniform_pants": "Jujutsu High Uniform Pants",
    "jujutsu_uniform_shoes": "Jujutsu High Uniform Shoes", "gojo_blindfold": "Gojo's Blindfold", "gojo_jacket": "Gojo's Jacket",
    "gojo_pants": "Gojo's Pants", "gojo_shoes": "Gojo's Shoes", "sukuna_kimono": "Sukuna's Kimono", "sukuna_hakama": "Sukuna's Hakama",
    "toji_shirt": "Toji's Shirt", "toji_pants": "Toji's Pants", "toji_shoes": "Toji's Shoes", "nanami_jacket": "Nanami's Suit Jacket",
    "nanami_slacks": "Nanami's Slacks", "nanami_shoes": "Nanami's Oxfords", "mahito_patchwork_top": "Mahito's Patchwork Top",
    "mahito_patchwork_pants": "Mahito's Patchwork Pants", "geto_kesa": "Geto's Kesa", "geto_hakama": "Geto's Hakama",
}.items():
    L[f"item.jjk.{k}"] = v
for k, v in {
    "jujutsu_uniform": "+5% CE regeneration per piece",
    "gojo_outfit": "Blindfold: -15% CE cost (Six Eyes). Full set: -25% cost, +15% regen",
    "sukuna_kimono": "+10% technique damage per piece. Requires 10 of Sukuna's fingers",
    "toji_outfit": "Heavenly Restriction: +6% speed and +1.5 damage per piece",
    "nanami_suit": "Full set: +30% melee damage at night (overtime)",
    "mahito_patchwork": "Slowly reshapes your body (regeneration)",
    "geto_kesa": "2 pieces: curses ignore you unless you strike first",
}.items():
    L[f"item.jjk.armor_set.{k}.bonus"] = v
L["jjk.message.kimono_rejects"] = "The kimono rejects you. (Requires %s of Sukuna's fingers)"

# ================================================================ Phase 6: NPCs
NPCS = {
    "gojo": ("Satoru Gojo", "Yo! Don't worry, I'm the strongest. Want to get stronger too? I can teach you a thing or two.",
             "Reverse Cursed Technique: multiply negative energy by negative energy. Black Flash: hit at the exact same instant. Easy, right?"),
    "yuji": ("Yuji Itadori", "Hey! You're a sorcerer too? Let's exorcise some curses together!", ""),
    "megumi": ("Megumi Fushiguro", "...You're from Jujutsu High? Keep it quick, there are curses nearby.", ""),
    "nobara": ("Nobara Kugisaki", "Finally, someone useful. Got any work, or are you just sightseeing?", ""),
    "nanami": ("Kento Nanami", "It's almost six. Let's settle this efficiently, without overtime.", ""),
    "maki": ("Maki Zen'in", "Need gear? Cursed tools are my specialty. Don't break them.", ""),
    "panda": ("Panda", "I'm a panda. Don't think too hard about it. Want some talismans?", ""),
    "inumaki": ("Toge Inumaki", "Salmon! ...Tuna mayo. (He seems to be offering help.)", ""),
    "yaga": ("Masamichi Yaga", "Why do you want to be a jujutsu sorcerer? Answer that, and I'll train you.",
             "Training is not for the faint of heart. Show me you've earned it."),
}
for k, (name, greet, train) in NPCS.items():
    L[f"entity.jjk.{k}"] = name
    L[f"item.jjk.{k}_spawn_egg"] = f"{name} Spawn Egg"
    L[f"jjk.npc.{k}.greeting"] = greet
    L[f"jjk.npc.{k}.train"] = train or "Train? Talk to Gojo or Principal Yaga."
L["entity.jjk.curse_user"] = "Curse User"
L["item.jjk.curse_user_spawn_egg"] = "Curse User Spawn Egg"
L.update({
    "jjk.npc.button.missions": "Missions", "jjk.npc.button.trade": "Trade", "jjk.npc.button.train": "Train",
    "jjk.npc.button.bye": "Goodbye", "jjk.npc.button.back": "Back", "jjk.npc.button.accept": "Accept mission",
    "jjk.npc.button.turn_in": "Report mission", "jjk.npc.button.rct": "Learn Reverse Cursed Technique",
    "jjk.npc.button.rct_done": "RCT learned", "jjk.npc.button.black_flash": "Train Black Flash",
    "jjk.npc.button.black_flash_done": "Black Flash trained",
    "jjk.npc.need_id": "Show a registered Jujutsu High Student ID to trade.",
    "jjk.npc.refuse_exiled": "\"You reek of Sukuna. Get away from me.\"",
    "jjk.npc.mission_active": "How's the mission going? %s",
    "jjk.npc.mission_offer": "I have a job for you: %s Reward: %s jujutsu experience, cursed fragments and supplies.",
    "jjk.mission.exorcise": "Exorcise %s curses of %s or stronger (%s done).",
    "jjk.mission.retrieve_finger": "Retrieve one of Sukuna's fingers and hand it over for sealing.",
    "jjk.mission.already_active": "You already have an active mission.",
    "jjk.mission.accepted": "Mission accepted: %s",
    "jjk.mission.ready": "Mission complete! Report to any sorcerer.",
    "jjk.mission.need_finger": "You need one of Sukuna's fingers.",
    "jjk.mission.not_done": "Not finished yet: %s",
    "jjk.mission.complete": "Mission complete! +%s jujutsu experience.",
    "jjk.train.rct_requirements": "Requires Grade 1 and 3 Special Grade Cursed Objects.",
    "jjk.train.black_flash_requirements": "Requires Grade 3 and 20 exorcised curses.",
    "jjk.train.rct_learned": "You learned Reverse Cursed Technique! (G to heal)",
    "jjk.train.black_flash_learned": "Your sense for Black Flash sharpens. (+3% Black Flash chance)",
})
for k, (t, d) in {
    "ten_missions": ("Reliable Sorcerer", "Complete 10 missions"),
    "reverse_cursed_technique": ("Negative Times Negative", "Learn Reverse Cursed Technique"),
}.items():
    L[f"advancements.jjk.{k}.title"] = t
    L[f"advancements.jjk.{k}.description"] = d

with open(sys.argv[1], "w", encoding="utf-8") as f:
    json.dump(dict(sorted(L.items())), f, indent=2, ensure_ascii=False)
print(len(L), "keys")

//! Procedurally generated textures, so the game needs no sprite files to look
//! good. Everything is white/greyscale and tinted at draw time with palette
//! colours (HDR tints make them bloom).

use bevy::asset::RenderAssetUsages;
use bevy::image::Image;
use bevy::prelude::*;
use bevy::render::render_resource::{Extent3d, TextureDimension, TextureFormat};

#[derive(Resource, Clone)]
pub struct VfxTextures {
    /// Soft radial falloff: particles, glows, motes.
    pub soft_circle: Handle<Image>,
    /// Thin glowing ring: shockwaves, crosshair, telegraphs.
    #[allow(dead_code)] // used by the crosshair (Stage 2) and telegraphs
    pub ring: Handle<Image>,
}

impl FromWorld for VfxTextures {
    fn from_world(world: &mut World) -> Self {
        let mut images = world.resource_mut::<Assets<Image>>();
        Self {
            soft_circle: images.add(radial_image(64, |d| (1.0 - d).clamp(0.0, 1.0).powf(2.2))),
            ring: images.add(radial_image(128, |d| {
                // Gaussian band centred at 85% radius.
                let x = (d - 0.85) / 0.06;
                (-x * x).exp()
            })),
        }
    }
}

/// Build a square RGBA image where alpha = `f(normalised distance from centre)`.
fn radial_image(size: u32, f: impl Fn(f32) -> f32) -> Image {
    let mut data = Vec::with_capacity((size * size * 4) as usize);
    let c = (size as f32 - 1.0) / 2.0;
    for y in 0..size {
        for x in 0..size {
            let dx = (x as f32 - c) / c;
            let dy = (y as f32 - c) / c;
            let d = (dx * dx + dy * dy).sqrt();
            let a = if d > 1.0 { 0.0 } else { f(d) };
            data.extend_from_slice(&[255, 255, 255, (a.clamp(0.0, 1.0) * 255.0) as u8]);
        }
    }
    Image::new(
        Extent3d { width: size, height: size, depth_or_array_layers: 1 },
        TextureDimension::D2,
        data,
        TextureFormat::Rgba8UnormSrgb,
        RenderAssetUsages::RENDER_WORLD,
    )
}

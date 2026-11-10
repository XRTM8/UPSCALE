use crate::{
    Error, ExportOptions, Fit, MediaInfo, Platform, PostKind, Quality, Resolution, Result,
};
use std::{ffi::OsString, path::Path};

#[derive(Debug, Clone)]
pub struct ExportPlan {
    pub width: u32,
    pub height: u32,
    pub filter: String,
    pub options: ExportOptions,
    pub video_stream: u32,
}

impl ExportPlan {
    pub fn new(info: &MediaInfo, options: ExportOptions) -> Result<Self> {
        if info.hdr {
            return Err(Error::Invalid("HDR export requires a verified tone-mapping pipeline and is not enabled in this first version.".into()));
        }
        if !info.duration_seconds.is_finite()
            || info.duration_seconds <= 0.
            || !info.frame_rate.is_finite()
            || info.frame_rate <= 0.
        {
            return Err(Error::Invalid("Invalid video timing.".into()));
        }
        if options.platform == Platform::Youtube && options.post == PostKind::Story {
            return Err(Error::Invalid(
                "YouTube does not have a Story export preset. Choose Reel/Shorts or landscape."
                    .into(),
            ));
        }
        if matches!(options.platform, Platform::Instagram | Platform::Facebook)
            && options.resolution == Resolution::Uhd
        {
            return Err(Error::Invalid(
                "Use the 1080p preset for Instagram and Facebook in this version.".into(),
            ));
        }
        let (short, long) = if options.resolution == Resolution::Uhd {
            (2160, 3840)
        } else {
            (1080, 1920)
        };
        let (width, height) = if options.post == PostKind::Landscape {
            (long, short)
        } else {
            (short, long)
        };
        let mut filters = vec![];
        if info.interlaced {
            filters.push("bwdif=mode=send_frame:parity=auto:deint=interlaced".to_string());
        }
        if options.denoise {
            filters.push("hqdn3d=1.2:1.2:3:3".to_string());
        }
        if options.smooth && info.frame_rate < 59. {
            // Interpolation needs future frames. Supply its look-ahead at EOF,
            // then trim the padding so the final source frames are retained.
            filters.push("tpad=stop=3:stop_mode=clone".into());
            filters
                .push("minterpolate=fps=60:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1".into());
            filters.push(format!("trim=duration={:.9}", info.duration_seconds));
        } else if info.frame_rate > 60. {
            filters.push("fps=60".into());
        }
        match options.fit {
            Fit::Contain => {
                filters.push(format!("scale={width}:{height}:force_original_aspect_ratio=decrease:force_divisible_by=2:flags=lanczos"));
                filters.push(format!(
                    "pad={width}:{height}:(ow-iw)/2:(oh-ih)/2:color=black"
                ));
            }
            Fit::Cover => {
                filters.push(format!("scale={width}:{height}:force_original_aspect_ratio=increase:force_divisible_by=2:flags=lanczos"));
                filters.push(format!("crop={width}:{height}"));
            }
        }
        filters.push("setsar=1".into());
        if options.detail {
            filters.push("unsharp=5:5:0.35:5:5:0.0".into());
        }
        filters.push("format=yuv420p".into());
        Ok(Self {
            width,
            height,
            filter: filters.join(","),
            options,
            video_stream: info.video_stream,
        })
    }

    pub fn args(&self, input: &Path, temporary_output: &Path) -> Vec<OsString> {
        let mut args: Vec<OsString> = [
            "-hide_banner",
            "-nostdin",
            "-y",
            "-v",
            "warning",
            "-protocol_whitelist",
            "file,pipe",
            "-i",
        ]
        .into_iter()
        .map(Into::into)
        .collect();
        args.push(input.as_os_str().into());
        let (preset, crf) = if self.options.quality == Quality::Maximum {
            ("medium", "17")
        } else {
            ("fast", "20")
        };
        let maxrate = if self.options.resolution == Resolution::Uhd {
            "60M"
        } else {
            "20M"
        };
        let bufsize = if self.options.resolution == Resolution::Uhd {
            "120M"
        } else {
            "40M"
        };
        for arg in [
            "-map".to_string(),
            format!("0:{}", self.video_stream),
            "-map".into(),
            "0:a:0?".into(),
            "-vf".into(),
            self.filter.clone(),
            "-c:v".into(),
            "libx264".into(),
            "-preset".into(),
            preset.into(),
            "-crf".into(),
            crf.into(),
            "-maxrate".into(),
            maxrate.into(),
            "-bufsize".into(),
            bufsize.into(),
            "-c:a".into(),
            "aac".into(),
            "-b:a".into(),
            "192k".into(),
            "-ar".into(),
            "48000".into(),
            "-movflags".into(),
            "+faststart".into(),
            "-map_metadata".into(),
            "-1".into(),
            "-map_chapters".into(),
            "-1".into(),
            "-progress".into(),
            "pipe:1".into(),
            "-nostats".into(),
        ] {
            args.push(arg.into());
        }
        args.push(temporary_output.as_os_str().into());
        args
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn media() -> MediaInfo {
        MediaInfo {
            name: "source.mp4".into(),
            width: 1280,
            height: 720,
            display_width: 1280,
            display_height: 720,
            duration_seconds: 4.,
            frame_rate: 30.,
            codec: "h264".into(),
            has_audio: true,
            hdr: false,
            interlaced: false,
            size_bytes: 1,
            video_stream: 0,
        }
    }

    #[test]
    fn contain_preserves_content_while_cover_is_explicit() {
        let contain = ExportPlan::new(&media(), ExportOptions::default()).unwrap();
        assert!(
            contain
                .filter
                .contains("force_original_aspect_ratio=decrease")
        );
        assert!(contain.filter.contains("pad=1080:1920"));
        let options = ExportOptions {
            fit: Fit::Cover,
            ..Default::default()
        };
        assert!(
            ExportPlan::new(&media(), options)
                .unwrap()
                .filter
                .contains("crop=1080:1920")
        );
    }

    #[test]
    fn hdr_and_incompatible_presets_fail_before_starting_a_process() {
        let mut info = media();
        info.hdr = true;
        assert!(ExportPlan::new(&info, ExportOptions::default()).is_err());
        assert!(
            ExportPlan::new(
                &media(),
                ExportOptions {
                    resolution: Resolution::Uhd,
                    ..Default::default()
                }
            )
            .is_err()
        );
    }

    #[test]
    fn interpolation_requires_opt_in_and_does_not_duplicate_a_60fps_source() {
        assert!(
            !ExportPlan::new(&media(), ExportOptions::default())
                .unwrap()
                .filter
                .contains("minterpolate")
        );
        let mut info = media();
        let options = ExportOptions {
            smooth: true,
            ..Default::default()
        };
        assert!(
            ExportPlan::new(&info, options.clone())
                .unwrap()
                .filter
                .contains("minterpolate")
        );
        info.frame_rate = 60.;
        assert!(
            !ExportPlan::new(&info, options)
                .unwrap()
                .filter
                .contains("minterpolate")
        );
    }
}

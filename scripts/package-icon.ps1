param()
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing.Common,System.Drawing.Primitives,System.ComponentModel.Primitives,System.Private.Windows.GdiPlus,System.Private.Windows.Core,System.Console -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

public static class LauncherIconPackager {
    static readonly Color Navy = Color.FromArgb(13, 22, 28);
    static Graphics Quality(Bitmap bitmap) {
        Graphics g = Graphics.FromImage(bitmap);
        g.InterpolationMode = InterpolationMode.HighQualityBicubic;
        g.CompositingQuality = CompositingQuality.HighQuality;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.PixelOffsetMode = PixelOffsetMode.HighQuality;
        return g;
    }
    static Bitmap Tile(Bitmap foreground, int size, bool round) {
        var result = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (var g = Quality(result)) {
            if (round) {
                using (var path = new GraphicsPath()) {
                    path.AddEllipse(0, 0, size, size);
                    g.SetClip(path);
                }
            }
            using (var brush = new SolidBrush(Navy)) g.FillRectangle(brush, 0, 0, size, size);
            // Launcher masks display the central 72/108 of the adaptive layers.
            // Produce the equivalent crop for legacy PNGs and the store artwork.
            g.DrawImage(foreground, new RectangleF(-size / 4f, -size / 4f, size * 1.5f, size * 1.5f));
        }
        return result;
    }
    public static void Run(string root) {
        string assetDir = Path.Combine(root, "assets", "app-icon");
        string resDir = Path.Combine(root, "app", "src", "main", "res");
        using (var source = new Bitmap(Path.Combine(assetDir, "source.png"))) {
            int left = source.Width, top = source.Height, right = -1, bottom = -1;
            for (int y = 0; y < source.Height; y++) for (int x = 0; x < source.Width; x++) {
                if (source.GetPixel(x, y).A < 8) continue;
                left = Math.Min(left, x); top = Math.Min(top, y);
                right = Math.Max(right, x); bottom = Math.Max(bottom, y);
            }
            if (right < left || source.GetPixel(0, 0).A != 0)
                throw new InvalidOperationException("Expected a nonempty transparent icon foreground.");
            double cx = (left + right) / 2.0, cy = (top + bottom) / 2.0, radius = 0;
            for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
                if (source.GetPixel(x, y).A < 8) continue;
                radius = Math.Max(radius, Math.Sqrt((x-cx)*(x-cx) + (y-cy)*(y-cy)));
            }
            // Keep the visible symbol within the central 60dp diameter safe circle
            // on the 108dp adaptive layer. Preserve the source alpha and proportions.
            double scale = (1024.0 * 30.0 / 108.0) / radius;
            using (var foreground = new Bitmap(1024, 1024, PixelFormat.Format32bppArgb)) {
                using (var g = Quality(foreground)) {
                    g.Clear(Color.Transparent);
                    g.DrawImage(source, new RectangleF((float)(512-cx*scale), (float)(512-cy*scale),
                        (float)(source.Width*scale), (float)(source.Height*scale)));
                }
                string drawableDir = Path.Combine(resDir, "drawable-nodpi");
                Directory.CreateDirectory(drawableDir);
                foreground.Save(Path.Combine(drawableDir, "ic_launcher_foreground.png"), ImageFormat.Png);
                int[] sizes = {48, 72, 96, 144, 192};
                string[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
                for (int i = 0; i < sizes.Length; i++) {
                    string dir = Path.Combine(resDir, "mipmap-" + densities[i]);
                    Directory.CreateDirectory(dir);
                    using (var square = Tile(foreground, sizes[i], false)) square.Save(Path.Combine(dir, "ic_launcher.png"), ImageFormat.Png);
                    using (var round = Tile(foreground, sizes[i], true)) round.Save(Path.Combine(dir, "ic_launcher_round.png"), ImageFormat.Png);
                }
                using (var artwork = Tile(foreground, 512, false)) artwork.Save(Path.Combine(assetDir, "mirror-selfie-icon.png"), ImageFormat.Png);
                using (var preview = new Bitmap(720, 300, PixelFormat.Format32bppArgb)) {
                    using (var g = Quality(preview)) {
                        g.Clear(Color.FromArgb(234, 240, 245));
                        using (var round = Tile(foreground, 220, true)) g.DrawImageUnscaled(round, 30, 20);
                        using (var square = Tile(foreground, 220, false)) {
                            using (var path = new GraphicsPath()) {
                                const int x=280, y=20, s=220, d=80;
                                path.AddArc(x,y,d,d,180,90); path.AddArc(x+s-d,y,d,d,270,90);
                                path.AddArc(x+s-d,y+s-d,d,d,0,90); path.AddArc(x,y+s-d,d,d,90,90);
                                path.CloseFigure(); g.SetClip(path); g.DrawImageUnscaled(square,x,y); g.ResetClip();
                            }
                        }
                        using (var small = Tile(foreground, 48, true)) g.DrawImageUnscaled(small, 586, 106);
                        using (var font = new Font("Segoe UI", 12)) using (var brush = new SolidBrush(Navy)) {
                            g.DrawString("Circle", font, brush, 115, 255);
                            g.DrawString("Rounded square", font, brush, 325, 255);
                            g.DrawString("48 px", font, brush, 586, 255);
                        }
                    }
                    preview.Save(Path.Combine(assetDir, "launcher-preview.png"), ImageFormat.Png);
                }
            }
            Console.WriteLine("Icon alpha bounds: {0},{1} to {2},{3}; adaptive scale {4:F3}", left, top, right, bottom, scale);
        }
    }
}
'@
[LauncherIconPackager]::Run((Split-Path -Parent $PSScriptRoot))

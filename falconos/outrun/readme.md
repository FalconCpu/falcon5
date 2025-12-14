# Background

The FalconAmiga is my attempt at imagining what the Amiga could have been if Commodore had continued to develop it aggressively into the mid 1990s. Then try to implement it using modern FPGA.

In summary, the FalconAmiga has:
* A 125Mhz RISC CPU     
    * 16Kb instruction and data caches
    * Floating point unit
    * region based memory management unit
* 64Mb of Chip RAM (PC-125 standard)
* Blitter with support for affine transformations and clip rects
* Support 640x480x8bit graphics mode in chunky or planar modes, with hardware support for window compositing.
* 8 Channel audio with 8 or 16 bit samples
* Copper similar to the original Amiga (but now 32 bit)

# Outrun

After seeing ReAssembler's YouTube video of the challenges of porting OutRun to the Amiga (https://www.youtube.com/watch?v=5PpR-Dm3-nU&t=1220s), it got me wondering how much easier it would be to target the FalconAmiga.

So this is my attempt at porting OutRun - gratefully using a lot of the work done by ReAssembler as a basis.  I'm not trying to be faithful to the original arcade game - I'm more interested in seeing how well my hardware can handle a game like this.

The combination of chunky graphics mode and blitter support for sprite scaling should make a game like this far easier to implement than on a standard Amiga.

# My approach

Outrun used a raster road rendering technique - where a bitmap of the road is drawn one horizontal line at a time, with each line offset horizontally or vertically to give the impression of 3D perspective. I didn't go that way for my version.

Instead I used ReAssembler's LayOut tool to export the road and scenery data from the original game as an XML file. I then processed that in a Python to convert the road data as a set of piecewise quadratic curves for the road X and Y coordinates as a function of Z distance along the road, along with a flat list of scenery objects with their world coordinates.

Then each frame I calculate the road coordinates for 50 points along the road ahead of the car, then linearly interpolate between those points to get the road coordinates for each scanline to draw. This makes for a far smoother road rendering - as everything is calcultated in floating point and only converted to integer pixel coordinates at the last moment. And is also far easier to implement - at the cost being rather more CPU intensive.

Similarly for the scenery objects. I simply walk the list of objects, and check which are close and infront of the car, then calculate their screen coordinates and sizes based. The blitter is then used to scale and draw each object sprite at the calculated screen position - and clip any parts that are offscreen.

This is pretty heavy on the Blitter - but it still runs fine at a solid 60fps at 640x480 resolution. My current implementation of the blitter is not particularly optimised, at best it can process one pixel per clock cycle - wheras the memory bandwidth would allow for 2 pixels per clock cycle. So there is still room for improvement there.
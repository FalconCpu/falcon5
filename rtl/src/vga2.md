# Next Gen Video Architecture (VGA2)

The graphics system is capable of displaying 256 Visual Objects (VOBS) on a scanline. Each VOB can be be rectangular, triangular, or Rhomboid in shape. Each VOB can be filled with a gradient color or a bitmap image. Each VOB has a programmable Z-order to determine which VOB appears on top when they overlap. VOBS can be recycled from one scanline to another to allow more than 256 VOBS on a frame.

Registers per VOB. 

Fixed point values are in 16.16 2's complement format.


| Name | Format   | Description            |
|------|----------|------------------------|
| YPOS | 16/16    | Vertical position of the VOB on the screen. The upper 16 bits specify the starting Y coordinate(inclusive), and the lower 16 bits specify the ending Y coordinate (exclusive). Anything outside this range will not be drawn. Set YPOS to 0x7FFF_XXXX to disable the VOB.
| CTRL | 32  | Mode of the VOB(Solid fill/Bitmap). See below for more details.
| XCLIP | 16/16   | Horizontal clipping of the VOB. The upper 16 bits specify the starting X coordinate(inclusive), and the lower 16 bits specify the ending X coordinate (exclusive). Anything outside this range will not be drawn
| SRC_ADDR | 32   | For bitmap data - Base address of the bitmap. For solid fill the color value is stored here
| SRC_STRIDE | 32  | For bitmap data - Number of bytes per row of the bitmap. For solid fill - Second color value
| X1 | 16.16  | X coordinate of the left edge of the VOB. (2's complement Fixed point)
| X2 | 16.16  | X coordinate of the right edge of the VOB
| Z  | 16.16  | Z order of the VOB. Lower values are drawn on top of higher values.
| U  | 16.16  | U coordinate for bitmap image
| V  | 16.16  | V coordinate for bitmap image
| dZdx | 16.16 | Change in Z per pixel in the X direction.
| dUdx | 16.16 | Change in U per pixel in the X direction.
| dVdx | 16.16 | Change in V per pixel in the X direction
| dX1dy | 16.16 | Change in X1 per scanline in Y direction
| dX2dy | 16.16 | Change in X2 per scanline
| BRK_dXdY | 16.16 | New value for dX1dy or dX2dy when a breakpoint is hit
| dZdy | 16.16 | Change in Z per scanline
| dUdy | 16.16 | Change in U per scanline
| dVdy | 16.16 | Change in V per scanline


The CTRL register is formatted as follows:-
| Bits | Name        | Description                     |
|------|-------------|---------------------------------|
| 2:0  | MODE        | 0=Solid Fill, 1=Bitmap, others=Reserved 
| 3    | TRANSPARENCY| For bitmap images, specifies if pixel value 0 is 0=Opaque, 1=Transparent
| 5:4  | PALETTE     | Selects one of 4 palettes for bitmap images
| 15:3 | reserved    |                                 |
| 25:16| BRK_Y       | Y coordinate of breakpoint. When the current scanline reaches this value, dX1dy or dX2dy is updated to BRK_dXdY. For the break scanline the average of the old and new dX is used.
| 26  | BRK_SEL      | 0=Update dX1dy, 1=Update dX2dy  |


The mode bits determine how a pixel is colored. 

If MODE=0 then the pixel is filled with a solid color. The color is interpolated between the two 24-bit color values stored in SRC_ADDR and SRC_STRIDE based on the U value for the pixel. int(U)=0 selects the first color, int(U)=255 selects the second color, and values in between interpolate linearly.

If MODE=1 then the pixel color is fetched from a bitmap image. An address is calulated as follows:-

    ADDR = SRC_ADDR + (int(V) * SRC_STRIDE) + int(U)

A byte is read from this address. This byte is used as an index into one of 4 palettes (selected by the PALETTE bits in CTRL) to get the final color value for the pixel. If TRANSPARENCY is set and the byte read from the bitmap is 0, then the pixel is not drawn (the pixel below it shows through).



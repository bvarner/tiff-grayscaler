tiff-grayscaler
===============

Command-line Java app to convert TIFF files to indexed grayscale color spaces: greatly reducing the size of the file


h2. Building

`mvn clean package`

h2. Running
From the command line....

`java -jar target/tiff-grayscaler-1.0-SNAPSHOT-shaded.jar <levels> <infile> <outfile>`

Where:

* levels: The number of gray levels you want in the output image.
* infile: Path to the input file (TIFF)
* outfile: Path to write the file to (TIFF)

h2. Full Sample: Building & Running

The following commands build, download a TIFF (CCTI v4 I believe) to test things out with, and run the program.

```
mvn clean package
wget http://docmorph.nlm.nih.gov/docview/distrib/v43n3a1.tif
java -jar target/tiff-grayscaler-1.0-SNAPSHOT-shaded.jar 16 v43n3a1.tif v43n3a1-indexedcm.tif
```

Now, check the output sizes:

```
$ ls -al
-rw-r--r--@  1 bvarner  staff    287103 Oct  9 11:11 v43n3a1-indexedcm.tif
-rw-r--r--@  1 bvarner  staff    321258 Apr 16  1997 v43n3a1.tif
```

At 16 levels of gray, things are pretty decent. Experiment with 24, 32, or other values if you feel the need.

Obviously, the higher the resolution the image, or more detailed the color depth, the bigger the savings - This works really well on pages scanned in full color that aren't actually 'color' pages, and can save a _ton_ in archival costs if you're archiving lots of scanned images.

The savings also pass on to pdf if you're later converting the TIFF to pdf format, since the PDF will also use an indexed color model if one exists in the source.

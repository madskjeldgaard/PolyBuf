# PolyBuf

An easy way to load a bunch of audio files into easily accessible collections of
buffers in Supercollider. Great for sample libraries.

This currently contains two classes: BufFiles and BufFolders. Both explained
below.

TODO: Add a third class, PolyBuf, which recursively goes through an entire
directory tree. BufFiles and BufFolders currently only work at the root level of
the path supplied to their path argument.

## Usage

`BufFiles(server, path)`

Returns an array of `Buffer`'s loaded with the audio files from the root of the given path. 

`BufFolders(server, path)`

Returns a dict of `Buffer`'s loaded with the audio files from the folders of audio files at the given path. 

Each folder's contents are accessible as an array of buffers at the dict key of the same name as the folder.

Example: 
```
b = BufFolders(s, "path/to/808/sample/pack");

b[\808bd]; // A list of all 808 kick drums in the 808bd folder in the supplied path
b[\808hh]; // A list of all 808 high hats in the 808hh folder in the supplied path

b[\root]; // Contains all of the audio files at the root level (ie outside of the directories at path)

c = BufFiles(s, "path/to/808/sample/pack/808sd");

c; // Now this is just an array of files from the 808sd folder above

// Play using a pbind 
Pbind(
    \instrument, \bufferPlayer, // (you need to make a bufferPlayer synth to make this work
    \buffer, Pxrand(b[\808bd], inf), // Randomly choose from the kick drum samples
    \dur, Pseq([0.25, 0.5, 0.25, 0.125, 0.125],inf) // A terrible rhythm
).play;

```


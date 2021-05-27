BufFiles {
	var <buffers, 
	supportedHeaders = #[
		"aiff",
		"wav",
		"wave", 
		"riff",
		"sun", 
		"next",
		"sd2",
		"ircam",
		"raw",
		"mat4",
		"mat5",
		"paf",
		"svx",
		"nist",
		"voc",
		"w64",
		"pvf",
		"xi",
		"htk",
		"sds",
		"avr",
		"flac",
		"caf"
	];

	var action, verbosity;

	*new { arg server, path, channel, normalize=true, actionWhenDone, verbose=true;
		if(server.hasBooted, { 
			^super.new.init(server, path, channel, normalize, actionWhenDone, verbose);
		}, {

			"Server has not been booted. % will not return before booted".format(this.class.name).warn;
			server.doWhenBooted{
				^super.new.init(server, path, channel, normalize, actionWhenDone, verbose);
			}
		})

	}

	init { arg server, path, channel, normalize, actionWhenDone, verbose;
		verbosity = verbose;
		action = actionWhenDone;
		buffers = this.loadBuffersToArray(server, path, channel, normalize);
		^buffers
	}

	// This is a workaround: 
	// Implement it's own version of the .at method 
	// to allow access to the buffer array whenever the fork is finished processing
	at {|index|
		^buffers[index]
	}

	numBuffers{
		^buffers.size
	}

	checkHeader { |path|
		^supportedHeaders.indexOfEqual(
			PathName(path).extension.toLower
		).notNil;
	}

	loadBuffersToArray { arg server, path, channel, normalized;

		// Iterate over all entries in the folder supplied by the path 
		// arg and select the files that seem to be audio files
		var paths = PathName(path).files.select({|soundfile| 
			this.checkHeader(soundfile.fullPath)
		});

		verbosity.if({  
			"Loading % soundfiles from % \n".format(paths.size, path).postln 
		});

		// And for all audio files, load the file into a buffer 
		fork {
			var condition = Condition.new;

			server.sync;

			buffers = paths.collect{|soundfile|
				var buffer;
				var thisPath = soundfile.fullPath;
				// Wait for server to catch up

				buffer = if (channel.notNil) {
					Buffer.readChannel(server,
						thisPath,
						// startFrame: 0,
						// numFrames: -1,
						// bufnum: nil,
						channels: [channel],
						action: {
							condition.unhang
						}
					)
				} {
					Buffer.read(server,
						thisPath,
						// startFrame: 0,
						// numFrames: -1,
						// bufnum: nil,
						action: {
							condition.unhang
						}
					)
				};

				condition.hang;
				verbosity.if({
					"LOADED: %".format(thisPath).postln;
				});

				buffer
			};

			// Normalize buffers
			// server.sync;
			buffers = if(normalized, { buffers.collect{|b| b.normalize} }, { buffers });

			// Clean up: The files found that aren't audio files will leave a
			// slot in the buffers array with the value 'nil'. This will remove those. 
			// server.sync;
			buffers = buffers.reject({|item| item.isNil });

			// Then, if some of the buffers for some reason don't have any frames in them, remove those as well
			// server.sync;
			buffers = buffers.reject({|item| 
				if(item.numFrames == 0, { 
					"Buffer read from % is empty. It will be removed and freed".format(item.path).warn;
					item.free;
					true
				}, {
					false
				})
			});


			server.sync;
			// Call done action
			action.value();
		}
	}

	// Pattern convenience functions
	asPseq{|repeats=1, offset=0|
		^Pseq(buffers, repeats, offset)
	}

	asPrand{|repeats=1|
		^Prand(buffers, repeats)
	}

	asPxrand{|repeats=1|
		^Pxrand(buffers, repeats)
	}

	asPwalk{|stepPattern, directionPattern=1, startPos=0|
		^Pwalk(buffers, stepPattern, directionPattern, startPos)
	}

	asPshuf{|repeats=1|
		^Pshuf(buffers, repeats)
	}

	// Demand rate convenience functions
	asDseq{|repeats=1, offset=0|
		^Dseq(buffers, repeats, offset)
	}

	asDrand{|repeats=1|
		^Drand(buffers, repeats)
	}

	asDxrand{|repeats=1|
		^Dxrand(buffers, repeats)
	}

	asDshuf{|repeats=1|
		^Dshuf(buffers, repeats)
	}
}

BufFolders {

	var <dict; 

	*new { arg server, path, normalize=true;

		^super.new.init(server, path, normalize);

	}

	init { arg server, path, normalize;
		if(server.hasBooted, { 
			dict = Dictionary.new;
			this.loadDirTree(dict, server, path, normalize);
		}, {
			"Server has not been booted... aborting buffer loading".error;
		});
	}

	at{|key|
		^dict[key]
	}

	loadDirTree {|dict, server, path, normalize|
		fork{
			PathName(path).folders.collect{|item|
				server.sync;

				// Load folder of sounds into an array at dict key of the folder name
				item.isFolder.if{ 
					var dictkey = item.folderName.asSymbol;

					"Found folder: %".format(item).postln;
					dict.add(
						dictkey -> BufFiles.new(server: server, path: item.fullPath, normalize: normalize)
					);
				};

				// If it's a file
				// item.isFile.if{ ("file! " ++ item).postln };

			};

			server.sync;

			// Load files at the root of the folder if any
			this.loadRootFiles(dict, server, path, normalize);

			server.sync;
			this.info();
		}
	}        

	info{
		// Info
		dict.keysValuesDo{|k,v| "Key % contains % buffers".format(k,v.buffers.size).postln};
	}

	// If the root of the folder contains files, add them to the key \root
	loadRootFiles {|dict, server, path, normalize|
		if(PathName(path).files.size > 0, {
			dict.add(\root -> BufFiles(server, path, normalize: normalize))
		})
	}

}

// TODO
/*
PolyBuf {
    
        var dict; 

        *new { arg server, path;

            ^super.new.init(server, path);

        }

        init { arg server, path;
            dict = Dictionary.new;

            ^this.loadContentsOfDir(dict, server, path);
        }

        loadDirTree {

            // Every level of the Directory tree: loadContents

        }

        loadContentsOfDir { arg d, server, path;

            case
                {this.containsFilesAndFolders(path)}{ 
                    var files_at_root = BufFiles.new(server, path); 
                    var subdict = BufFolders.new(server, path);

                    var cwd = PathName(path).folderName.asSymbol; 

                    subdict.add(\root -> files_at_root);

                    d.add(cwd -> subdict);

                    "files and folders !".postln; 

                }
                {this.containsFolders(path)}{ 
                    var folders = BufFolders.new(server, path);
                    var cwd = PathName(path).folderName.asSymbol; 

                    d.add(cwd -> folders);

                    "folders!".postln; 

                }
                {this.containsFiles(path)}{ 
                    var files = BufFiles.new(server, PathName(path).entries.files);
                    var cwd = PathName(path).folderName.asSymbol; 

                    d.add(cwd -> files);

                    "files".postln;

                };

                ^d;
        }

        containsFilesAndFolders { arg path;
            ^(this.containsFiles(path) && this.containsFolders(path))
        }

        containsFolders { arg path;
            var result = PathName(path).folders.size > 0;
            
            ("Supplied path contains subdirectories: " ++ result).postln;
            
            ^result
        }
        
        containsFiles { arg path;
            var result = PathName(path).files.size > 0;
            
            ("Supplied path contains files at root: " ++ result).postln;
            
            ^result
        }

}
*/

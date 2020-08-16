// Create an array of buffers
// Arguments: server, path

BufFiles {
	
    var <buffers, supportedHeaders = #[
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

        *new { arg server, path, normalize=true;
            ^super.new.init(server, path, normalize);
        }

        init { arg server, path, normalize;
			buffers = this.loadBuffersToArray(server, path, normalize);

			^buffers
        }

		// This is a workaround: 
		// Implement it's own version of the .at method 
		// to allow access to the buffer array whenever the fork is finished processing
		at {|index|
			^buffers[index]
		}

        checkHeader { |path|
            ^supportedHeaders.indexOfEqual(
                PathName(path).extension.toLower
            ).notNil;
        }

        loadBuffersToArray { arg server, path, normalized;

            // Iterate over all entries in the folder supplied by the path 
			// arg and select the files that seem to be audio files
			var paths = PathName(path).files.select({|soundfile| 
                this.checkHeader(soundfile.fullPath)
            });

			// And for all audio files, load the file into a buffer 
			fork {
				buffers = paths.collect{|soundfile|
					// Wait for server to catch up
					server.sync;

					Buffer.read(server, 
						soundfile.fullPath, 
						startFrame: 0, 
						numFrames: -1, 
						action: nil, 
					)
				};

				// Normalize buffers
				server.sync;
				buffers = if(normalized, { buffers.collect{|b| b.normalize} }, { buffers });

				// Clean up: The files found that aren't audio files will leave a
				// slot in the buffers array with the value 'nil'. This will remove those. 
				server.sync;
				buffers = buffers.reject({|item| item.isNil });

				// Then, if some of the buffers for some reason don't have any frames in them, remove those as well
				server.sync;
				buffers = buffers.reject({|item| 
					if(item.numFrames == 0, { 
						"Buffer read from % is empty. It will be removed and freed".format(item.path).warn;
						item.free;
						true
					}, {
						false
					})
				});

			}
		}
    }

BufFolders {
        
        var dict; 

        *new { arg server, path;

            ^super.new.init(server, path);

        }

        init { arg server, path;

            dict = Dictionary.new;

            ^this.loadDirTree(dict, server, path);

        }
        
        loadRootFiles {|dict, server, path|
            // If the root of the folder contains files, add them to the key \root

            if(PathName(path).files.size > 0, 
                dict.add(\root -> BufFiles(server, path) );
            )

        }

        loadDirTree {|dict, server, path|

            PathName(path).folders.collect{|item|

                // Load folder of sounds into an array at dict key of the folder name
                item.isFolder.if{ 
                    dict.add(item.folderName.asSymbol -> BufFiles.new(server, item.fullPath));
                };

                // If it's a file
                item.isFile.if{ ("file! " ++ item).postln };

            };

            this.loadRootFiles(dict, server, path);

            // Info
            dict.keysValuesDo{|k,v| "Key % contains % buffers now".format(k,v.size).postln};

            ^dict;

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

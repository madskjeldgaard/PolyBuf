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
	],
    <folderName;

    // GUI stuff
    var <>selected, <>selectionAction;

	var action, verbosity;

	*new { arg server, path, channel, normalize=true, actionWhenDone, verbose=true;
		^super.new.init(server, path, channel, normalize, actionWhenDone, verbose, nil);
	}

    // Create an instance from an existing array of loaded Buffers
    *newFromBufferArray{ arg bufferArray;
        ^super.new.init(preloadedBuffers: bufferArray);
    }

    *newFromListOfPaths{ arg server, pathList, channel, normalize=true, actionWhenDone, verbose=true;
        ^super.new.init(server, pathList, channel, normalize, actionWhenDone, verbose, nil);
    }

	init { arg server, path, channel, normalize, actionWhenDone, verbose, preloadedBuffers;
        selected = [];
        selectionAction = selectionAction ? {};
		verbosity = verbose;
		action = actionWhenDone;

        // Allow passing in an array of preloaded buffers
        preloadedBuffers.isNil.if({
            buffers = this.loadBuffersToArray(server, path, channel, normalize);
        }, {
            buffers = preloadedBuffers;
        });

		^this
	}

	// This is a workaround:
	// Implement it's own version of the .at method
	// to allow access to the buffer array whenever the fork is finished processing
	at {|index|
		^buffers[index]
	}

    first{
        ^buffers.first
    }

    last{
        ^buffers.last
    }

    size{
        ^this.numBuffers
    }

	numBuffers{
		^buffers.size
	}

	checkHeader { |path|
		^supportedHeaders.indexOfEqual(
			path.extension.toLower
		).notNil;
	}

	loadBuffersToArray { arg server, path, channel, normalized;
        var paths;

		// Iterate over all entries in the folder supplied by the path
		// arg and select the files that seem to be audio files
        if(path.isKindOf(Array), {

            paths = path.collect{|pathIn| PathName(pathIn) }.select{|pathIn|
                this.checkHeader(pathIn)
            };

            folderName = "buffiles";
        }, {
            path = if(path.class != PathName, {PathName(path)}, { path });
            paths = path.files.select({|soundfile|
                this.checkHeader(soundfile)
            });

            folderName = path.fileName;
        });

		verbosity.if({
			"Loading % soundfiles from % \n".format(paths.size, path).postln
		});

		// And for all audio files, load the file into a buffer
        server.waitForBoot{
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

    gui{
        var win = Window.new(name: "PolyBufGUI");
        var preview = false;
        var bufNames = this.buffers.collect{|buf, bufIndex|
            PathName(buf.path).fileName -> bufIndex
        }.asDict;

        var titlebar = StaticText.new(win).string_(folderName).font_(Font.default.bold_(true));
        var helpText = StaticText.new(win).string_("Click soundfile name to preview.\nMake selection using ctrl and shift.\nSelected buffers are available in .selected of this object.");
        var list = ListView.new(win)
        .items_(
            bufNames.keys.collect{|k| k.asString }.asArray
        ).action_({|obj|
            var index = obj.value;
            var fn = list.items[index];
            var polybufIndex = bufNames.at(fn);

            if(preview, {
                this.at(polybufIndex).play;
            });
        })
        .selectionMode_(\extended)
        .selectionAction_({|obj|
            var indices = obj.selection;
            var fns = indices.collect{|index| list.items[index] };
            var polybufIndices = fns.collect{|fn| bufNames.at(fn) };
            this.selected = polybufIndices.collect{|index| this.at(index)};
            this.selectionAction.value(this.selected);
        });

        var previewbutton = Button.new(win)
        .states_([["preview", Color.black, Color.red], ["preview",Color.black, Color.green]])
        .action_({|obj|
            preview = obj.value.asBoolean;
        });

        var close = Button.new(win).states_([["close"]]).action_({ win.close });
        var layout = VLayout.new(titlebar, helpText,previewbutton, list, close);

        win.layout = layout;
        win.front();
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

    choose{
        ^buffers.choose
    }

    playRandom{
        ^this.choose.play
    }

    // Search collection and return a subset of buffers
    findAll{|fileNamesThatContainString|
        var subCollection = Array.new;

        fileNamesThatContainString.isNil.not.if{

            buffers.do{|buffer|
                var path = PathName(buffer.path).fileNameWithoutExtension;
                var isMatch = fileNamesThatContainString.asString.matchRegexp(path);
                if(isMatch, {
                    subCollection = subCollection.add(buffer)
                })
            }
        };

        ^this.class.newFromBufferArray(subCollection)
    }

    // Returns the first occurence
    find{|fileNameThatContainsString|
        var subCollection = this.findAll(fileNameThatContainsString);

        ^subCollection.first
    }

}

BufFolders {

	var <dict, action, <folderName;

	*new { arg server, path, normalize=true, actionWhenDone;
		^super.new.init(server, path, normalize, actionWhenDone);

	}

	init { arg server, path, normalize, actionWhenDone;
		dict = IdentityDictionary.new;
		action = actionWhenDone;

        path = if(path.class != PathName, {PathName(path)}, { path });

        server.doWhenBooted{
            this.loadDirTree(server, path, normalize);
        }

	}

	at{|key|
		^dict[key]
	}

    loadDirTree {|server, path, normalize|
        var condition = Condition.new;
        var folders = path.deepFolders;

        folderName = path.fileName;
        server.sync;

        "Loading folders: ".postln;
        folders.do{|f| ("\t" ++ f.fullPath).postln };

        folders.do{|item|

            // Load folder of sounds into an array at dict key of the folder name
            // item.isFolder.if{
            //     item.folders.do{|fold|
                    var dictkey = item.folderName.asSymbol;
                    if(item.files.size != 0, {
                        "Found folder: %\n".format(dictkey).postln;

                        "It contains: % files\n".format(item.files.size).postln;

                        dict[dictkey] = BufFiles.new(server: server, path: item, normalize: normalize, actionWhenDone: {
                            condition.unhang
                        });

                        condition.hang;

                        "Done loading folder %".format(item.folderName).postln;

                    })
            //     }
            // };

            // If it's a file
            // item.isFile.if{ ("file! " ++ item).postln };

        };

        server.sync;

        // Load files at the root of the folder if any
        this.loadRootFiles(dict, server, path, normalize);

        action.value();
    }

	// If the root of the folder contains files, add them to the key \root
	loadRootFiles {|dict, server, path, normalize|
		if(path.files.size > 0, {
			var cond = Condition.new;
			"Found files in root of folder, putting them in \root ".postln;
			dict.add(\root -> BufFiles(server, path, normalize: normalize, actionWhenDone: { cond.unhang }));
			cond.hang;
		})
	}

    gui{
        var win = Window.new(name: this.folderName);
        var titlebar = StaticText.new(win).string_(folderName).font_(Font.default.bold_(true));
        var helpText = StaticText.new(win).string_("Click folder name to preview and make selections.");

        var buttons = this.dict.keys.asArray.collect{|buffilesName|
            var key = buffilesName;
            var buffiles = this.at(key);
            Button.new(win)
            .states_([[key]])
            .action_({
                buffiles.gui
            });
        };

        var close = Button.new(win).states_([["close"]]).action_({ win.close });
        var objects = [titlebar] ++ [helpText] ++ buttons ++ [nil] ++ [close];
        var layout = VLayout(*objects);

        win.layout = layout;

        win.front();
    }

}

// TODO
// PolyBuf : IdentityDictionary {
//     var <folders;
//
//     *new{ arg server, path, channel, normalize=true, actionWhenDone, verbose=true;
//         ^super.new.init(server, path, channel, normalize, actionWhenDone, verbose);
//     }
//
//     init { arg server, path, channel, normalize, actionWhenDone, verbose;
//         // From parent class
//         dictionary = this.newInternalNode;
//
//         if(path.class != PathName && path.isKindOf(String), { path = PathName(path) });
//         folders = path.folders;
//     }
//
// }

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

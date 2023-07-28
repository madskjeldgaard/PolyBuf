Bufdef : BufFiles{
    var <key, <>bufFiles;
    var <>inPath;

    classvar <>all;

    *new{ arg key, server, path, channel, normalize=true, actionWhenDone, verbose=true;
        var res = this.at(key);
        if(res.isNil) {
            res = super.new(server, path, channel, normalize, actionWhenDone, verbose).prAdd(key);
            res.inPath = path;
        } {
            // Trigger a new BufFiles if the path is different
            if(path != res.inPath) {
                res.clear();
                res = super.new(server, path, channel, normalize, actionWhenDone, verbose).prAdd(key);
            }
        };

        ^res
    }

    *at{|key|
        ^all[key]
    }

    *initClass {
        all = IdentityDictionary.new;
    }

    replace{|server, pathOrListOfPaths, channel, normalize=true, actionWhenDone, verbose=true, preloadedBuffers|
        this.clear();
        this.init(server, pathOrListOfPaths, channel, normalize, actionWhenDone, verbose, preloadedBuffers);
    }

    dup { |n = 2| ^{ this }.dup(n) } // avoid copy in Object::dup

    *hasGlobalDictionary { ^true }

    prAdd { arg argKey;
        key = argKey;
        all.put(argKey, this);
    }
}

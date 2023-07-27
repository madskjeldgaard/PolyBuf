+ PathName {
	deepFolders {
		var out;
		this.entries.do { | item |
			if(item.isFolder) {
				out = out.add(item);
				out = out.addAll(item.deepFolders);
			}
		};
		^out
	}
}

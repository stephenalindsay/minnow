# minnow

Minnow is a simple programmer's editor for Clojure.

NOTE: Very much alpha software. 
===============================
There will be bugs. You have been warned.  
=========================================  
  
Minnow is intended to be a comfortable first editor for folks new to Clojure who don't already 
have a strong attachment to any of the other Clojure development environments.

Minnow has the following features:

- project browser
- integrated repl
- source highlighting
- auto-indenting
- code re-indenting 
- easy to install/run from leiningen

The project relies heavily on the work of the folks behind:

- Seesaw (http://github.com/daveray/seesaw)
- RSyntaxTextArea (http://fifesoft.com/rsyntaxtextarea) 
- nREPL (http://github.com/clojure/tools.nrepl)

Minnow has drawn ideas and inspiration from the excellent work done on the Enclojure 
(http://enclojure.org) and Clooj (https://github.com/arthuredelstein/clooj) projects. 
Minnow was bootstrapped using VimClojure (https://bitbucket.org/kotarak/vimclojure). 
If Minnow is not to your liking those projects are definitely worth your consideration.

## Usage

Minnow can be launched via [leiningen.](http://leiningen.org/#install) 

Add lein-minnow to your ~/.lein/profiles.clj 
```clojure
    {:user {:plugins [[lein-minnow "0.1.4"]]}}
```

Then run:
```bash
    lein minnow
```

## Status

Very much a work in progress, 

Tested on Ubuntu, untested on Windows or Mac.

## Keyboard shortcuts (there will be changes)

Shift-Ctl-R      Start repl for a project from project list  
Ctl-W            Close current editor window  
Alt-Down         Skip to next form  
Alt-Up           Skip to previous form  
Ctl-PageUp/Down  Move to next editor tab  
Shift-Ctl-F      Evaluate file  
Ctl-N		 Set repl namespace to that of file with focus  
Ctl-S            Save file  
Ctl-W            Close file  
Ctl-F            Start find dialog  
Shift-Ctl-R      Start REPL dialog  
Shift-Ctl-A      Toggle output pane position  
Ctl-E            Set focus to editor window  
Ctl-P	         Set focus to project explorer  
Ctl-R            Set focus to repl  

## License

Copyright (C) 2012 Steve Lindsay

Distributed under the Eclipse Public License, the same as Clojure.



require 'pathname'

sources = FileList['src/**/*.scala']
tmpjar = 'build/convert.jar'
optjar = 'build/cvs-fast-export.jar'
libjar = 'build/scala-library.jar'

scala_home = ENV['SCALA_HOME'] || Pathname.new(`which scala`.chomp).realpath.dirname.dirname.to_s

file tmpjar => sources do |t|
  mkdir_p 'build/classes'
  sh "fsc -Xlog-implicits -deprecation -d build/classes -sourcepath src #{sources}"
  sh "jar -cfe #{t.name} org.rvaughn.scm.cvs.FastExport -C build/classes ."
end

file libjar do
  mkdir_p 'build'
  cp File.join(scala_home, 'lib/scala-library.jar'), 'build'
end

file optjar => [tmpjar, libjar] do
  sh "proguard @proguard.cfg"
end

desc 'builds the default, unbundled jar'
task :default => tmpjar

desc 'builds the program with Scala libraries bundled in'
task :bundle => optjar

desc 'deletes all build outputs'
task :clean do
  rm_rf 'build'
end

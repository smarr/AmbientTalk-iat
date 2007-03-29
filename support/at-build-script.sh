#!/usr/bin/env bash

# builds the AmbientTalk/2 distribution.
# uses the following variables:
# OUTPUT_PATH = directory where the new build should be placed
# AT2JAR_PATH = directory where the ambienttalk2.jar file can be found
# AT2LIB_PATH = directory where the 'at' system library can be found
# AT2IAT_PATH = directory where iat and other support files can be found
# EXT_LIBS = all files to be included as external libraries under the 'lib' output folder
# UPLOAD_USR = name of the user to connect to prog to upload the build

OUTPUT_PATH="/Users/tvc/Desktop"
AT2JAR_PATH="/Users/tvc/Desktop"
AT2LIB_PATH="/Users/tvc/EclipseWorkspace/AT2\\ Library/edu/vub"
AT2IAT_PATH="/Users/tvc/EclipseWorkspace/InteractiveAT/support"
EXT_LIBS="/Users/tvc/EclipseWorkspace/InteractiveAT/lib/java-getopt-1.0.13.jar:/Users/tvc/EclipseWorkspace/AT2\\ Interpreter/lib/antlr.jar:/Users/tvc/EclipseWorkspace/AT2\\ Interpreter/lib/JGroups-2.4.0.bin"
UPLOAD_USR="tvcutsem"

# 1) create a new directory at2-buildddmmyy

dat=`date +%d%m%y`
dirname="at2-build$dat"
target="$OUTPUT_PATH/$dirname"
echo "mkdir $target"

# 2) include ambienttalk2.jar

echo "cp $AT2JAR_PATH/ambienttalk2.jar $target"

# 3) include iat and other support files and make iat executable

SUPPORT=`ls $AT2IAT_PATH/iat $AT2IAT_PATH/license $AT2IAT_PATH/readme`
for SUP in $SUPPORT;
do
	echo "cp $SUP $target"
done
echo "cp $AT2IAT_PATH/TextMate/AmbientTalk2.tmbundle $target"
echo "chmod a+x $target/iat"

# 4) include the library files

libdir="$target/lib"
echo "mkdir $libdir"

# TODO: parse paths in ':'
IFS=": "
set EXT_LIBS
for i in `echo $1`; do echo $i; done

for LIB in $EXT_LIBS;
do
	echo "cp $LIB $libdir"
done

# 5) include the 'at' native library

echo "cp $AT2LIB_PATH/at $target"

# 6) zip the directory

echo "zip -r $target $dirname"

# 7) notify user of succesfull build

echo "Build created: $OUTPUT_PATH/${dirname}.zip"

# 8) upload the zip to prog2

echo "scp $target/$dirname.zip ${UPLOAD_USR}@prog.vub.ac.be/home/www/prog/amop/downloads/${dirname}.zip"

# 9) report succesfull upload

echo "Build successfully uploaded. Don't forget to modify download link on the wiki"

# 10) tag the files in the SVN repository with the build name
tag() {
  local projectname=$1
  echo "svn copy -rHEAD svn://progmc13.vub.ac.be/repository/$projectname/trunk svn://progmc13.vub.ac.be/repository/$projectname/tags/$dirname"
}

tag "parser"
tag "interpreter"
tag "atlib"
tag "iat"

#echo "svn copy -rHEAD svn://progmc13.vub.ac.be/repository/parser/trunk svn://progmc13.vub.ac.be/repository/parser/tags/$dirname"
#echo "svn copy -rHEAD svn://progmc13.vub.ac.be/repository/interpreter/trunk svn://progmc13.vub.ac.be/repository/interpreter/tags/$dirname"
#echo "svn copy -rHEAD svn://progmc13.vub.ac.be/repository/atlib/trunk svn://progmc13.vub.ac.be/repository/atlib/tags/$dirname"
#echo "svn copy -rHEAD svn://progmc13.vub.ac.be/repository/iat/trunk svn://progmc13.vub.ac.be/repository/iat/tags/$dirname"
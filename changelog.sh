#!/bin/bash


# The bash snippet below formats the changes between two versions
# This prints a list of changes, reformatted to turn issue references into Markdown-formatted links to GitHub. This makes it convenient to copy and paste when writing a change log.
# Some lines may be missing GitHub issue references. You'll need to decide for each one whether to find an issue to link it to in the change log, include it in the change log without an issue link, or omit it from the change log entirely. Merge commits and automated commits that only change the version number should always be left out.

# Example usage:
# produce change log starting from tag 1.3.8 up to HEAD
# ./changelog.sh 1.3.8

# produce change log starting from tag 1.3.7 up tag 1.3.8
# ./changelog.sh 1.3.7 1.3.8


FROM=$1
if [ $2 ]; then
  TO=$2
else
  TO=HEAD
fi

echo "Changelog from $FROM to $TO"
  git log --pretty=format:"%s %an" $FROM..$TO | sed 's/\(.*\)(#\([0-9]*\)) \(.*\)/\* [\2](https:\/\/github.com\/lagom\/lagom\/issues\/\2) \1(\3)/'

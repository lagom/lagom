
FROM=$1
if [ $2 ]; then
  TO=$2
else
  TO=HEAD
fi

echo "Changelog from $FROM to $TO"
  git log --pretty=format:"%s %an" $FROM..$TO | sed 's/\(.*\)(#\([0-9]*\)) \(.*\)/\* [\2](https:\/\/github.com\/lagom\/lagom\/issues\/\2) \1(\3)/'

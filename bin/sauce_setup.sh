OSNAME=`uname -s`
OSARCH=`uname -m`

mkdir target
cd target

if [[ $OSNAME == *"Linux"* ]];
then
  if [[ $OSARCH == *"64"* ]];
  then
    wget https://saucelabs.com/downloads/sc-4.4.2-linux.tar.gz
    tar -xzf sc-4.4.2-linux.tar.gz
    mv sc-4.4.2-linux sc-dist
  else
    if [[ $OSARCH == *"86"* ]];
    then
      wget https://saucelabs.com/downloads/sc-4.4.2-linux32.tar.gz
      tar -xzf sc-4.4.2-linux32.tar.gz
      mv sc-4.4.2-linux32 sc-dist
    fi
  fi
else
  if [[ $OSNAME == *"Darwin"* ]];
  then
    wget https://saucelabs.com/downloads/sc-4.4.2-osx.zip
    unzip sc-4.4.2-osx.zip
    mv sc-4.4.2-osx sc-dist
  fi
fi

cd ..


JAVA_FILES=$(shell find src -name "*.java")
ANT ?= ant
JAVA ?= java
JAVAC ?= javac

all: libs/galil-java.jar

test: libs/galil-java.jar t/Test1.class
	$(JAVA) -enableassertions -classpath "t:libs/galil-java.jar" Test1

libs/galil-java.jar: ${JAVA_FILES} build.xml
	$(ANT) dist

t/%.class: t/%.java libs/galil-java.jar
	$(JAVAC) -g -classpath "t:libs/galil-java.jar" $<

check:
	JAVA_CMD=$(shell which java) checkstyle -c conf/checkstyle.xml  -r src 2>&1 | grep -v '^log4j:WARN'

clean:
	$(ANT) clean
	rm -f t/*.class

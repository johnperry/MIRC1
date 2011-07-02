//*************************** Useful functions ***************************
//
var IE = document.all;
//
//Find an object and return its size and position.
//If the object is the body, then return the visible
//dimensions rather than the total size of the page.
function findObject(obj) {
	var objPos = new Object();
	objPos.obj = obj;
	if (obj == document.body) {
		 if (window.innerWidth) {
			//standards compliant
			objPos.w = window.innerWidth,
			objPos.h = window.innerHeight
		 }
		 else if ( (document.documentElement) &&
			 	   (document.documentElement.clientWidth) &&
			 	   (document.documentElement.clientWidth != 0) ) {
			//IE6 in standards mode
			objPos.w = document.documentElement.clientWidth,
			objPos.h = document.documentElement.clientHeight
		 }
		 else {
		 	// older versions of IE
			objPos.w = document.body.clientWidth,
			objPos.h = document.body.clientHeight
		}
	}
	else {
		objPos.h = obj.offsetHeight;
		objPos.w = obj.offsetWidth;
	}
	var curleft = 0;
	var curtop = 0;
	if (obj.offsetParent) {
		curleft = obj.offsetLeft
		curtop = obj.offsetTop
		while (obj = obj.offsetParent) {
			curleft += obj.offsetLeft
			curtop += obj.offsetTop
		}
	}
	objPos.x = curleft;
	objPos.y = curtop;
	return objPos;
}

function getEvent(theEvent) {
	return (theEvent) ? theEvent : window.event;
}

function stopEvent(theEvent) {
	if(document.all) theEvent.cancelBubble = true;
	else theEvent.stopPropagation();
}

function getSource(theEvent) {
	return (document.all) ? theEvent.srcElement : theEvent.target;
}

function getDestination(theEvent) {
	return (document.all) ? theEvent.toElement : theEvent.relatedTarget;
}

//Get the top of an element in its parent's coordinates
function getOffsetTop(theElement) {
	return theElement.offsetTop;
}

//Get the displayed width of an object
function getObjectWidth(obj) {
	return obj.offsetWidth;
}

//Get the displayed height of an object
function getObjectHeight(obj) {
	return obj.offsetHeight;
}

function openURL(url,target) {
	window.open(url,target);
}

function setStatusLine(text) {
	window.status = text;
}


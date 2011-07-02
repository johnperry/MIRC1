//This section provides multi-browser support for certain
//commonly required functions.
var IE = document.all;

//Get the top of an element in its parent's coordinates
function getOffsetTop(theElement) {
	return theElement.offsetTop;
}

//Get the source of the event.
function getSource(theEvent) {
	return (IE) ?
		theEvent.srcElement : theEvent.target;
}

function getEvent(theEvent) {
	return (theEvent) ? theEvent : ((window.event) ? window.event : null);
}

function openURL(url,target) {
	window.open(url,target);
}

//Get the displayed width of an object
function getObjectWidth(obj) {
	return obj.offsetWidth;
}

//Get the displayed height of an object
function getObjectHeight(obj) {
	return obj.offsetHeight;
}
//Popup Package

var popupZIndex = 40;

//Display a popup, more or less centered in the visible area.
function showPopup(popupDivId, w, h, title, closeboxFile) {
	hidePopups();
	var bodyPos = findObject(document.body);
	var x = (bodyPos.w - w) / 2;
	var y = (bodyPos.h - h) * 2 / 5;
	if (y < 0) y = 0;
	var popup = document.getElementById(popupDivId);
	setPopupTitleBar(popup, title, closeboxFile);
	popup.style.width = w;
	popup.style.height = h;
	popup.style.left = x;
	popup.style.top = y;
	popup.style.visibility = "visible";
	popup.style.display = "block";
	popup.style.overflow = "hidden";
	popup.style.zIndex = popupZIndex;
}

//Display a popup frame and load it from a URL
function showPopupFrame(id, w, h, frId, frURL, title, closeboxFile) {
	showPopup(id, w, h, title, closeboxFile);
	var frame = document.getElementById(frId);
	frame.style.width = w - 30;
	frame.style.height = h - 55;
	window.open(frURL, frId);
}

//Hide all popups
function hidePopups() {
	var divs = document.getElementsByTagName("DIV");
	for (var i=divs.length-1; i>=0; i--) {
		var z = divs[i].style.zIndex;
		if (z == popupZIndex) {
			divs[i].style.visibility = "hidden";
			divs[i].style.display = "none";
		}
	}
}

//Insert a titlebar if a popup doesn't already have one
function setPopupTitleBar(popup, title, closeboxFile) {
	//find the first non-TextNode child
	var child = popup.firstChild;
	while ((child != null) && (child.nodeType == 3)) child = child.nextSibling;
	if ((child.tagName != "DIV") || (child.className != "titlebar")) {
		var titlebar = document.createElement("DIV");
		titlebar.className = "titlebar";
		titlebar.onmousedown = startPopupDrag;
		var closebox = document.createElement("DIV");
		closebox.className = "closebox";
		var img = document.createElement("IMG");
		img.setAttribute("src", closeboxFile);
		img.setAttribute("title", "Close");
		img.onclick = hidePopups;
		closebox.appendChild(img);
		titlebar.appendChild(closebox);
		title = ( (title == null) || (title == "")) ? "\u00A0" : title;
		var titleP = document.createElement("P");
		titleP.appendChild(document.createTextNode(title));
		titlebar.appendChild(titleP);
		popup.insertBefore(titlebar, popup.firstChild);
	}
}

//This handler starts the drag of a popup.
function startPopupDrag(event) {
	//Find the node to drag. This handler assumes that the onmousedown
	//event occurred in a popup titlebar, which must be a first-generation
	//child of the popup div to be dragged.
	event = getEvent(event);
	var node = getSource(event);
	//Find the DIV (it may be a level or so above the source node,
	//if the user happened to click on the title text in the titlebar).
	while ((node != null) && (node.tagName != "DIV")) node = node.parentNode;
	if ((node != null) && (node.className == "titlebar")) {
		//This must be the titlebar; start dragging its parent.
		dragPopup(node.parentNode, event);
	}
}

//Drag and drop a popup
function dragPopup(node, event) {
	deltaX = event.clientX - parseInt(node.style.left);
	deltaY = event.clientY - parseInt(node.style.top);
	if (document.addEventListener) {
		document.addEventListener("mousemove", dragIt, true);
		document.addEventListener("mouseup", dropIt, true);
	}
	else {
		node.attachEvent("onmousemove", dragIt);
		node.attachEvent("onmouseup", dropIt);
		node.setCapture();
	}
	if (event.stopPropagation) event.stopPropagation();
	else event.cancelBubble = true;
	if (event.preventDefault) event.preventDefault();
	else event.returnValue = false;

	function dragIt(evt) {
		if (!evt) evt = window.event;
		node.style.left = (evt.clientX - deltaX) + "px";
		node.style.top = (evt.clientY - deltaY) + "px";
		if (evt.stopPropagation) event.stopPropagation();
		else evt.cancelBubble = true;
	}

	function dropIt(evt) {
		if (!evt) evt = window.event;
		if (document.addEventListener) {
			document.removeEventListener("mouseup", dropIt, true);
			document.removeEventListener("mousemove", dragIt, true);
		}
		else {
			node.detachEvent("onmousemove", dragIt);
			node.detachEvent("onmouseup", dropIt);
			node.releaseCapture();
		}
		if (evt.stopPropagation) event.stopPropagation();
		else evt.cancelBubble = true;
	}
}

//************************* Dialog Popup Functions **************************
//
function showTextDialog(popupDivId, w, h, title, closeboxFile, heading, text, okHandler, cancelHandler) {
	var popup = document.getElementById(popupDivId);
	if (!popup) {
		var div = document.createElement("DIV");
		div.className = "content";
		var p = document.createElement("P");
		p.className = "indentedblock";
		p.appendChild(document.createTextNode(text));
		div.appendChild(p);
		showDialog(popupDivId, w, h, title, closeboxFile, heading, div, okHandler, cancelHandler);
	}
	else showPopup(popupDivId, w, h, title, closeboxFile);
}

function showDialog(popupDivId, w, h, title, closeboxFile, heading, div, okHandler, cancelHandler) {
	var popup = document.getElementById(popupDivId);
	if (!popup) {
		//The popup does not exist, create it.
		popup = document.createElement("DIV");
		popup.id = popupDivId;
		popup.className = "popup";

		var header = document.createElement("DIV");
		header.className = "content";
		popup.appendChild(header);

		if (heading) {
			var h1 = document.createElement("H1");
			h1.appendChild(document.createTextNode(heading));
			header.appendChild(h1);
		}

		popup.appendChild(div);

		if (okHandler || cancelHandler) {
			var footer = document.createElement("DIV");
			footer.className = "content";
			popup.appendChild(footer);
			p = document.createElement("P");
			p.className = "centeredblock";
			footer.appendChild(p);
			var button;

			if (okHandler) {
				button = document.createElement("INPUT");
				button.className = "stdbutton";
				button.type = "button";
				button.value = "OK";
				button.onclick = okHandler ? okHandler : hidePopups;
				p.appendChild(button);
			}

			if (cancelHandler) {
				p.appendChild(document.createTextNode("\u00A0\u00A0\u00A0"));

				button = document.createElement("INPUT");
				button.className = "stdbutton";
				button.type = "button";
				button.value = "CANCEL";
				button.onclick = cancelHandler;
				p.appendChild(button);
			}
		}

		document.body.appendChild(popup);
	}
	showPopup(popupDivId, w, h, title, closeboxFile);
}

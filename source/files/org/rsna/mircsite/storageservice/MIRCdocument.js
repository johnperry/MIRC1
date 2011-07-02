//This section provides multi-browser support for certain
//commonly required functions.

//Get the height of the window.
function getHeight() {
	return (IE) ?
		document.body.clientHeight : window.innerHeight - 10;
}

//Get the width of the window.
function getWidth() {
	return (IE) ?
		document.body.clientWidth : window.innerWidth;
}

//Get the displayed width of an object
function getObjectWidth(obj) {
	return obj.offsetWidth;
}

//Get the displayed height of an object
function getObjectHeight(obj) {
	return obj.offsetHeight;
}

//Open a url
function openURL(url,target) {
	window.open(url,target);
}
//end of the multi-browser functions

function copy(event) {
	if (IE) {
		var source = getSource(getEvent(event));
		var textarea = document.createElement("TEXTAREA");
		textarea.innerText = source.innerText;
		var copy = textarea.createTextRange();
		copy.execCommand("Copy");
	}
}

function copyTextToClipboard(text) {
	if (IE) {
		var textarea = document.createElement("TEXTAREA");
		textarea.innerText = text;
		var copy = textarea.createTextRange();
		copy.execCommand("Copy");
	}
}

window.onresize = window_resize;
window.onload = mirc_onload;
document.onkeydown = keyDown;
document.onkeyup = keyUp;

var horizontalSplit = null;
var verticalSplit = null;

function mirc_onload() {
	fixRadLexTermList();
	setBackground();
	if ((display == "mstf") || (display == "mirctf") || (display == "tab")) {
		initTabs();
		if (imageSection == "yes") loadImage(1);
		setIFrameSrc(currentPage);
		var pos = findObject(document.body);
		var leftWidth = pos.w / 4;
		if (display == 'tab')  leftWidth = 90;
		horizontalSplit = new HorizontalSplit("leftside", "divider", "rightside", true,
												leftWidth, true,
												1, 1, splitHandler);
		if (document.getElementById("sldiv")) {
			var leftside = document.getElementById("leftside");
			var h = parseInt(leftside.style.height);
			verticalSplit = new VerticalSplit("uldiv", "sldiv", "lldiv", 2*h/3, 1, 1);
		}
	}
	setWheelDriver();
	window.focus();
}

function splitHandler() {
	setSize();
	displayImage();
}

function fixRadLexTermList() {
for (var i=0; i<radlexTerms.length; i++) radlexTerms[i] = radlexTerms[i].toLowerCase();
	radlexTerms.sort();
}

function setBackground() {
	if (background == "light") {
		document.body.style.color = "black";
		document.body.style.background = "white";
	}
	else {
		document.body.style.color = "white";
		document.body.style.background = "black";
		document.body.setAttribute("link","white");
	}
}

//save images button handler
function saveImages(fileurl) {
	openURL(fileurl,"_self");
}

//export button zip extension handler
function exportZipFile(url,target, myEvent) {
	if (getEvent(myEvent).altKey) url += "&ext=mrz";
	openURL(url,target);
}

//document deletion function
function deleteDocument(url) {
	if (confirm("Are you sure you want\nto delete this document\nfrom the server?")) {
		openURL(url,"_self");
	}
}

//quiz answer event handler (toggle response)
function toggleResponse(divid) {
	var x = document.getElementById(divid);
	if (x.style.display == 'block') {
		x.style.display = 'none';
		x.style.visibility = 'hidden';
		if (!document.all) window.resizeBy(0,1);
	} else {
		x.style.display = 'block';
		x.style.visibility = 'visible';
		if (!document.all) window.resizeBy(0,-1);
	}
}

//jump button event handler for text-captions
function jumpButton(inc, myEvent) {
	var source = getSource(getEvent(myEvent));
	var buttons = source.parentElement.getElementsByTagName("input");
	var i;
	for (i=0; i<buttons.length; i++) {
		if (buttons[i] === source) {
			var b = i + inc;
			if ((b >= 0) && (b < buttons.length)) buttons[b].scrollIntoView(false);
			return;
		}
	}
}

//show button event handler for text-captions
function showButton(myEvent) {
	var source = getSource(getEvent(myEvent));
	var captionDiv = getPreviousNamedSibling(source,"div");
	if (captionDiv.className == "hide") {
		captionDiv.className="show";
    	if (inputType == "image")
			source.src = contextPath + "/buttons/hidecaption.png";
		else if (inputType == "button")
			source.value = "Hide Caption";
	} else {
		captionDiv.className="hide";
    	if (inputType == "image")
			source.src = contextPath + "/buttons/showcaption.png";
		else if (inputType == "button")
			source.value = "Show Caption";
	}
	source.scrollIntoView(false);
}

function getPreviousNamedSibling(start,name) {
	var prev = start.previousSibling;
	while ((prev != null) && (prev.tagName.toLowerCase() != name))
		prev = prev.previousSibling;
	return prev;
}

//initialize the tabs
function initTabs() {
	currentPage = document.getElementById('tab'+firstTab);
	currentButton = document.getElementById('b'+firstTab);
	if (currentPage == null) {
		currentPage = document.getElementById('tab1');
		currentButton = document.getElementById('b1');
	}
	currentPage.className = "show";
	currentButton.className = "s";
	if (showEmptyTabs != "yes") {
		var n = 2;
		var done = false;
		while (!done) {
			var b = document.getElementById("b"+n);
			if (b != null) {
				if (n != firstTab) {
					var d = document.getElementById("tab"+n);
					var count = 0;
					for (var q=0; q<d.childNodes.length; q++) {
						if (d.childNodes[q].nodeType == 1) count++;
					}
					if (count < 2) {
						b.style.display = 'none';
						b.style.visibility = 'hidden';
					}
				}
				n += 1;
			}
			else done = true;
		}
	}
}

//tab button event handler
var currentPage;
var currentButton;
function bclick(b,np) {
	currentButton.className = "u";
	currentButton = document.getElementById(b);
	currentButton.className = "s";
	var nextPage = document.getElementById(np);
	if (currentPage != nextPage) {
		currentPage.className="hide";
		currentPage = nextPage;
		currentPage.className="show";
		setIFrameSrc(currentPage);
		if (display == "tab") {
			var rightside = document.getElementById("rightside");
			if (rightside != null) {
				var imagetab = rightside.parentNode.id;
				var maindiv = document.getElementById("maindiv");
				if (np == imagetab) {
					maindiv.style.overflow = "hidden";
					horizontalSplit.positionSlider();
				}
				else
					maindiv.style.overflow = "auto";
			}
		}
	}
}

//set the src attribute of all the iframe children in an element.
//(this is just to avoid the error message from IE when an
//iframe is loaded in a non-visible tab and the iframe tries
//to grab the focus.)
function setIFrameSrc(el) {
	var iframes = el.getElementsByTagName("IFRAME");
	for (var i=0; i<iframes.length; i++) {
		var fr = iframes[i];
		var src = fr.getAttribute("src");
		var longdesc = fr.getAttribute("longdesc");
		if ((longdesc) && (longdesc != "")) {
			fr.src = longdesc;
			fr.setAttribute("longdesc", "");
		}
	}
}

function window_resize() {
	if (horizontalSplit) horizontalSplit.positionSlider();
}

//set the sizes for the components of the document
var rightsideWidth = 700;
var leftsideWidth = 256;

function setSize() {
	if ((display == "mstf") || (display == "mirctf") || (display == "tab")) {
		var bodyPos = findObject(document.body);
		var maindiv = document.getElementById('maindiv');
		var maindivPos = findObject(maindiv);
		var maindivHeight = bodyPos.h - maindivPos.y;
		if (maindivHeight < 100) maindivHeight = 100;
		maindiv.style.height = maindivHeight;

		if (imageSection == "yes") {
			var leftside = document.getElementById('leftside');
			var uldiv = document.getElementById('uldiv');
			var sldiv = document.getElementById("sldiv");
			var lldiv = document.getElementById('lldiv');
			var rightside = document.getElementById('rightside');
			var rbuttons = document.getElementById('rbuttons');
			var captions = document.getElementById('captions');
			var rimage = document.getElementById('rimage');

			var leftsidePos = findObject(leftside);
			if (leftsidePos.w) {
				if (uldiv) uldiv.style.width = leftsidePos.w;
				if (sldiv) sldiv.style.width = leftsidePos.w;
				if (lldiv) lldiv.style.width = leftsidePos.w;

				if ((uldiv != null) && (lldiv != null)) {
					if (verticalSplit) verticalSplit.positionSlider();
					/*
					var uldivHeight = leftsidePos.h * 2/3;
					uldiv.style.height = uldivHeight;
					lldiv.style.height = leftsidePos.h - uldivHeight;
					*/
				}
				else if ((uldiv == null) && (lldiv != null)) {
					lldiv.style.height = leftsidePos.h - 1;
				}
				else if ((uldiv != null) && (lldiv == null)) {
					uldiv.style.height = leftsidePos.h - 1;
				}

				var rightsidePos = findObject(rightside);
				rbuttons.style.width = rightsidePos.w;
				captions.style.width = rightsidePos.w;
				rimage.style.width = rightsidePos.w - 1;

				var rbuttonsPos = findObject(rbuttons);
				var captionsPos = findObject(captions);
				var rimageTop = rightsidePos.y + rbuttonsPos.h + captionsPos.h;
				var rimageHeight = maindivHeight - rbuttonsPos.h - captionsPos.h - 1;
				rimage.style.height = rimageHeight - 6;
			}
		}
	}
}

//display images in image-sections
//
function loadImage(image) {
	var index = image - 1;
	dehighlightToken();
	IMAGES.setCurrentIMAGESET(index);
	highlightToken();
	displayImage();
}

function loadAnnotation(image) {
	var index = image - 1;
	dehighlightToken();
	IMAGES.setCurrentIMAGESET(index);
	highlightToken();
	if (displayImage(image)) displayAnnotation();
}

//display the next image, if possible
function nextImage() {
	dehighlightToken();
	IMAGES.nextIMAGESET();
	highlightToken();
	displayImage();
}

//display the previous image, if possible
function prevImage() {
	dehighlightToken();
	IMAGES.prevIMAGESET();
	highlightToken();
	displayImage();
}

//dehighlight the current thumbnail
function dehighlightToken() {
	if (IMAGES.hasCurrentIMAGESET()) {
		var llc = document.getElementById("lldiv");
		if (llc) {
			var tokens = llc.getElementsByTagName("INPUT");
			tokens[IMAGES.currentIndex].className = "tokenbuttonDESEL";
		}
	}
}

function highlightToken() {
	if (IMAGES.hasCurrentIMAGESET()) {
		var llc = document.getElementById("lldiv");
		if (llc) {
			var tokens = llc.getElementsByTagName("INPUT");
			tokens[IMAGES.currentIndex].className = "tokenbuttonSEL";
		}
	}
}

function displayImage() {
	if (IMAGES.hasCurrentIMAGESET()) {
		var place = document.getElementById('rimagecenter');
		while (place.firstChild != null) place.removeChild(place.firstChild);
		var imageSet = IMAGES.currentIMAGESET;

		var image = imageSet.pImage;
		if (IMAGES.autozoom && imageSet.osImage) image = imageSet.osImage;
		var img = makeElement(image);
		place.appendChild(img);

		var imagenumber = document.getElementById('imagenumber');
		while (imagenumber.firstChild != null) imagenumber.removeChild(imagenumber.firstChild);
		imagenumber.appendChild(document.createTextNode("Image: " + (IMAGES.currentIndex+1)));

		setCaptions(imageSet);
		setSize();

		if (imageSet.hasAnnotation()) enableButton('annbtn','inline');
		else disableButton('annbtn','none');

		if (imageSet.osImage) enableButton('orgbtn','inline');
		else disableButton('orgbtn','none');

		if (imageSet.ofImage) enableButton('dcmbtn','inline');
		else disableButton('dcmbtn','none');

		if (!IMAGES.firstIsCurrent()) enableButton('previmg','inline');
		else disableButton('previmg','inline');

		if (!IMAGES.lastIsCurrent()) enableButton('nextimg','inline');
		else disableButton('nextimg','inline');

		imageSet.annotationDisplayed = false;

		return true;
	}
	return false;
}

function setCaptions(imageSet) {
	var capdiv = document.getElementById('captions');
	while (capdiv.firstChild != null) capdiv.removeChild(capdiv.firstChild);

	if (!imageSet.hasCaption()) {
		capdiv.style.display = "none";
	}
	else {
		capdiv.style.display = "block";
		if (imageSet.hasACaption()) {
			setCaption(capdiv, imageSet.aCaption, false);
		}
		if (imageSet.hasCCaption()) {
			if (imageSet.cFlag) setCaption(capdiv, imageSet.cCaption, false);
			else setCaption(capdiv, 'more...' ,true);
		}
	}
}

function setCaption(parent, caption, clickable) {
	var p = document.createElement("P");
	p.style.marginTop = 0;
	var t = document.createTextNode(caption);
	if (clickable) {
		p.onclick = showClickableCaption;
		var u = document.createElement("U");
		u.appendChild(t);
		p.appendChild(u);
	}
	else p.appendChild(t);
	parent.appendChild(p);
}

function showClickableCaption() {
	if (IMAGES.hasCurrentIMAGESET()) {
		var imageSet = IMAGES.currentIMAGESET;
		if (imageSet.hasCCaption()) {
			var capdiv = document.getElementById('captions');
			var pList = capdiv.getElementsByTagName("P");
			capdiv.removeChild(pList[pList.length-1]);
			setCaption(capdiv, imageSet.cCaption, false);
			imageSet.cFlag = true;
		}
	}
}

function disableButton(id, display) {
	var b = document.getElementById(id)
	b.disabled = true;
	b.style.backgroundColor = 'gray';
	b.style.fontWeight = 'bold';
	b.style.visibility = 'hidden';
	b.style.display = display;
}

function enableButton(id, display) {
	var b = document.getElementById(id)
	b.disabled = false;
	b.style.backgroundColor = 'dodgerblue';
	b.style.color = 'white';
	b.style.fontWeight = 'bold';
	b.style.visibility = 'visible';
	b.style.display = display;
}

function displayAnnotation() {
	if (IMAGES.hasCurrentIMAGESET()) {
		var imageSet = IMAGES.currentIMAGESET;
		if (imageSet.annotationDisplayed) displayImage();
		else {
			//Check the aImage and sImage values to determine
			//whether this is an SVG annotation or an image
			//with burned-in annotations. Give precedence to
			//the burned-in image because it loads faster and
			//doesn't require the SVG viewer.
			var place = document.getElementById('rimagecenter');
			if (imageSet.aImage.src != "") {
				//Image
				while (place.firstChild != null) place.removeChild(place.firstChild);
				place.appendChild(makeElement(imageSet.aImage));
				imageSet.annotationIsSVG = false;
				imageSet.annotationDisplayed = true;
			}
			else if (imageSet.sImage.src != "") {
				//SVG
				//Get the size of the image currently displayed.
				var img = place.getElementsByTagName("IMG")[0];
				if (img == null) return;
				var width = getObjectWidth(img);
				var height = getObjectHeight(img);

				//Embed the SVG file
				var svgEmbedID = "svgEmbedID";
				var embed = document.createElement("EMBED");
				embed.setAttribute("id",svgEmbedID);
				embed.setAttribute("src", imageSet.sImage.src);
				embed.setAttribute("width",width);
				embed.setAttribute("height",height);
				embed.setAttribute("type","image/svg+xml");
				while (place.firstChild != null) place.removeChild(place.firstChild);
				place.appendChild(embed);

				//Set up the root element.
				//Get the href for the base image.
				var href = imageSet.pImage.src;
				configureSVGRoot(svgEmbedID, href);
				imageSet.annotationIsSVG = true;
				imageSet.annotationDisplayed = true;
			}
		}
	}
}

function configureSVGRoot(id, href) {
	try {
		var embed = document.getElementById(id);
		var width = embed.getAttribute("width");
		var height = embed.getAttribute("height");
		var svgdoc = embed.getSVGDocument();

		var root = svgdoc.rootElement;
		root.setAttribute("width", width);
		root.setAttribute("height", height);
		var viewBox = "0 0 " + width + " " + height;
		root.setAttribute("viewBox",viewBox);

		var imageElements = root.getElementsByTagName("image");
		if (imageElements.length == 0) {
			var image = svgdoc.createElementNS("http://www.w3.org/2000/svg", "image");
			image.setAttributeNS("http://www.w3.org/1999/xlink","href", href);
			image.setAttribute("x", 0);
			image.setAttribute("y", 0);
			image.setAttribute("width", width);
			image.setAttribute("height", height);
			if (root.firstChild == null) root.appendChild(image);
			else root.insertBefore(image,root.firstChild);
		}
	}
	catch (ex) {
		setTimeout("configureSVGRoot(\""+id+"\",\""+href+"\");",10);
	}
}

function makeElement(image) {
	var elem = document.createElement("IMG");
	elem.src = image.src;
	if (IMAGES.autozoom) {
		var w = image.w;
		var h = image.h;
		if (w && h) {
			var rimage = document.getElementById("rimage");
			var rimagePos = findObject(rimage);
			var zh = rimagePos.h / h;
			var zw = rimagePos.w / w;
			var z = ((zh > zw) ? zw : zh);
			elem.width = z * w;
			elem.height = z * h;
		}
	}
	return elem;
}

function fetchOriginal() {
	if (IMAGES.hasCurrentIMAGESET()) {
		window.open(IMAGES.currentIMAGESET.osImage.src, "_blank");
	}
}

function fetchModality(myEvent) {
	if (IMAGES.hasCurrentIMAGESET()) {
		var imageSet = IMAGES.currentIMAGESET;
		myEvent = getEvent(myEvent)
		var imagePath = contextPath + dirPath + imageSet.ofImage.src;
		if (myEvent.altKey)
			//alt key generates a DICOM dataset dump
			openURL(imagePath+"?dicom","_blank");
		else if (myEvent.ctrlKey)
			//ctrl key downloads the file
			openURL(imagePath,"_self");
		else if (myEvent.shiftKey) {
			//shift key redisplays the current image
			//this removes the DICOM viewer applet, if loaded
			displayImage();
		}
		else {
			//no modifiers: request the DICOM viewer applet
			var place = document.getElementById('rimagecenter');
			while (place.firstChild != null) place.removeChild(place.firstChild);
			var pos = findObject(document.getElementById('rimage'));
			var rwidth = "" + pos.w;
			var rheight = "" + pos.h;
			var iframe = document.createElement("IFRAME");
			iframe.setAttribute("id", "viewerApplet");
			iframe.setAttribute("name", "viewerApplet");
			iframe.setAttribute("src", imagePath+"?viewer");
			iframe.setAttribute("width", rwidth);
			iframe.setAttribute("height", rheight);
			//now load the iframe
			place.appendChild(iframe);
		}
	}
}

function addParam(applet, name, value) {
	var param = document.createElement("PARAM");
	param.setAttribute(name,value);
	applet.appendChild(param);
}

//handle a link to a metadata file.
function fetchFile(url, myEvent) {
	if (!getEvent(myEvent).altKey) openURL(url,"_blank");
	else openURL(url+"?dicom","_blank");
}

//set a case of the day
function setCaseOfTheDay() {
	var url = document.getElementById("codurl").value;
	var title = document.getElementById("codtitle").value;
	var desc = document.getElementById("coddesc").value;
	var link = document.getElementById("codlink").value;
	var image = document.getElementById("codimage").value;
	var qs = "title=" + escape(title)
			+ "&description=" + escape(desc)
			+ "&link=" + escape(link)
			+ "&image=" + escape(image)
			+ "&timestamp=" + new Date().getTime();
	var req = new AJAX();
	req.POST(url, qs, codResult);

	function codResult() {
		if (req.success()) {
			alert("The document was entered as the Case of the Day.");
		}
	}
}

var lastAcceptedWheelTime = 0;
function setWheelDriver() {
	if ((display == "mstf") || (display == "mirctf") || (display == "tab")) {
		var rimage = document.getElementById("rimage");
		if (rimage) {
			if (rimage.addEventListener) {
				//Mozilla
				rimage.addEventListener('DOMMouseScroll', wheel, false);
				rimage.addEventListener("mousewheel", wheel, false);
			}
			else {
				// IE/Opera
				rimage.attachEvent("onmousewheel", wheel);
			}
			//set the handler for the home, end, and arrow keys
			document.onkeydown = keyDown;
			document.onkeyup = keyUp;
		}
	}
}

function wheel(event){
	if (!event) event = window.event;
	var delta = event.detail ? -event.detail : event.wheelDelta/40;
	if (delta < 0) delta = -1;
	if (delta > 0) delta = +1;
	var date = new Date();
	var currentTime = date.getTime();
	//alert("currentTime = "+currentTime+"\nlastAcceptedWheelTime = "+lastAcceptedWheelTime);
	if ((currentTime - lastAcceptedWheelTime) > 75) {
		if (!event.ctrlKey) loadImage(IMAGES.currentIndex + 1 - delta);
		else loadAnnotation(IMAGES.currentIndex + 1 - delta);
		lastAcceptedWheelTime = currentTime;
	}
	if (event.preventDefault) event.preventDefault();
	event.returnValue = false;
}

function keyDown(event) {
	if (!event) event = window.event;
	var nextImage = IMAGES.currentIndex + 1;
	var kc = event.keyCode;
	if (kc == 36) nextImage = 1; //HOME
	else if (kc == 35) nextImage = IMAGES.length(); //END
	else if (kc == 38) nextImage--; //UP ARROW
	else if (kc == 40) nextImage++; //DOWN ARROW

	else if ((kc == 109) || (kc == 189)) { //MINUS
		IMAGES.autozoom = false;
		displayImage();
		return;
	}

	else if ((kc == 107) || (kc == 187)) { //PLUS or EQUAL
		IMAGES.autozoom = true;
		displayImage();
		return;
	}

	else if (kc == 37) { //LEFT ARROW
		if (horizontalSplit) horizontalSplit.moveSlider(-10, displayImage);
		return;
	}
	else if (kc == 39) { //RIGHT ARROW
		if (horizontalSplit) horizontalSplit.moveSlider(+10, displayImage);
		return;
	}
	else if (kc == 33) { //PAGE UP
		if (horizontalSplit) horizontalSplit.moveSliderTo(1, displayImage);
		return;
	}
	else if (kc == 34) { //PAGE DOWN
		var pos = findObject(document.body);
		if (horizontalSplit) horizontalSplit.moveSliderTo(pos.w - imagePaneWidth - 7, displayImage);
		return;
	}

	if (kc != 17) {
		if (!event.ctrlKey) loadImage(nextImage);
		else loadAnnotation(nextImage);
	}
	else {	//annotations on current image
		if (!IMAGES.currentIMAGESET.annotationDisplayed) {
			displayAnnotation();
		}
	}
}
function keyUp(event) {
	if (!event) event = window.event;
	if (event.keyCode == 17) {
		if (IMAGES.currentIMAGESET.annotationDisplayed) displayImage();
	}
}

function exportToMyRsnaFiles(url) {
	url += "&myrsna";
	var req = new AJAX();
	req.GET(url, null, myRsnaResult);

	function myRsnaResult() {
		if (req.success()) {
			alert(req.responseText);
		}
	}
}

function newSearch() {
	var div = document.getElementById("radlexdiv");
	if (div) div.parentNode.removeChild(div);

	var div = document.createElement("DIV");
	div.className = "radlex";
	div.id = "radlex";
	div.appendChild(document.createElement("DIV"));

	var lastTerm = "";
	for (var i=0; i<radlexTerms.length; i++) {
		if (radlexTerms[i] != lastTerm) {
			var cb = document.createElement("INPUT");
			cb.type = "checkbox";
			div.appendChild(cb);
			div.appendChild(document.createTextNode(" "+radlexTerms[i]));
			div.appendChild(document.createElement("BR"));
			lastTerm = radlexTerms[i];
		}
	}
	div.appendChild(document.createElement("BR"));

	div.appendChild(document.createTextNode("Additional search text:"));
	div.appendChild(document.createElement("BR"));
	var text = document.createElement("INPUT");
	text.type = "text";
	text.style.width = "100%";
	div.appendChild(text);

	showDialog("radlexdiv", 500, 400,
				"Search on RadLex Terms",
				"/mirc/images/closebox.gif",
				"Select Terms for a New Search",
				div, doSearchRadLex, hidePopups);
}
function doSearchRadLex(event) {
	var div = document.getElementById("radlex");
	if (div == null) return;

	var query = "";
	var child = div.firstChild;
	while (child != null) {
		if ((child.nodeType == 1) && (child.tagName == "INPUT")) {
			if ((child.type == "checkbox") && child.checked) {
				child = child.nextSibling;
				query += child.nodeValue;
			}
			if (child.type == "text") query += " " + child.value;
		}
		child = child.nextSibling;
	}
	div = document.getElementById("radlexdiv");
	if (div) div.parentNode.removeChild(div);
	var req = new AJAX();
	//Find the server ID for the current context
	req.GET("/mirc/find", "context="+contextPath+"&"+req.timeStamp(), null);
	if (req.success()) {
		var xml = req.responseXML();
		var root = xml ? xml.firstChild : null;
		if ((root != null) && (root.tagName == "server")) {
			var server = root.getAttribute("id");
			//alert("server = "+server+"\n\n"+req.responseText);
			window.open("/mirc/query"
						+"?querypage=maincontent"
						+"&document="+query
						+"&server="+server
						+"&firstresult=1"
						+"&maxresults=50"
						+"&showimages="
						+"&queryUID=0"
						,"_self");
		}
	}
	else alert("Unable to find the storage service.");
}

var docURLForConference;
var docTitleForConference;
var docAltURLForConference;
var docAltTitleForConference;
var docSubtitleForConference;

function getTitle() {
	return title;
}

function addToConference(url, title, alturl, alttitle, subtitle) {
	docURLForConference = url;
	docTitleForConference = title;
	docAltURLForConference = alturl;
	docAltTitleForConference = alttitle;
	docSubtitleForConference = subtitle;

	var dy = IE ? 0 : 26;
	var dx = 0;
	var div = document.getElementById("treediv");
	if (div == null) {
		var div = document.createElement("DIV");
		div.className = "filetree";
		div.id = "filetree";
		div.appendChild(document.createElement("DIV"));

		showDialog("treediv", 600+dx, 400+dy, "Select Conference", "/mirc/images/closebox.gif", "Select a Conference", div, null, null);

		treeManager =
			new TreeManager(
				"filetree",
				"/file/service/tree",
				"/mirc/images/plus.gif",
				"/mirc/images/minus.gif");
		treeManager.load("files=no&myrsna=no");
		treeManager.display();
		treeManager.expandAll();
	}
	else showPopup("treediv", 600+dx, 400+dy, "Select Cabinet", "/mirc/images/closebox.gif");
}

function showConferenceContents(event) {  //note: this has a funny name because that's what the file service supplies in the tree.
	var source = getSource(getEvent(event));
	currentNode = source.treenode;
	var req = new AJAX();
	var qs = "nodeID="+currentNode.nodeID+
				"&url="+encodeURIComponent(docURLForConference)+
				"&title="+encodeURIComponent(docTitleForConference)+
					"&alturl="+encodeURIComponent(docAltURLForConference)+
					"&alttitle="+encodeURIComponent(docAltTitleForConference)+
						"&subtitle="+encodeURIComponent(docSubtitleForConference)+
							"&"+req.timeStamp()
	req.GET("/file/service/appendAgendaItem", qs, null);
	if (req.success()) {
		var xml = req.responseXML();
		var root = xml ? xml.firstChild : null;
		if ((root != null) && (root.tagName == "ok")) {
			alert("The MIRCdocument was added to the conference.");
			return;
		}
	}
	alert("Unable to add the MIRCdocument to the conference.");
}

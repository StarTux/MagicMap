const mapName = "map_name";
const worldBorder = world_border;
const scalingFactor = scaling_factor;

worldBorder.minX = (worldBorder.minX >> 9) << 9;
worldBorder.maxX = (worldBorder.maxX >> 9) << 9;
worldBorder.minZ = (worldBorder.minZ >> 9) << 9;
worldBorder.maxZ = (worldBorder.maxZ >> 9) << 9;

function calculateFrame() {
    const scrolling = document.getElementById("map_frame");
    const width = scrolling.clientWidth;
    const height = scrolling.clientHeight;
    const top = scrolling.scrollTop;
    const left = scrolling.scrollLeft;
    const rax = (worldBorder.minX + left / scalingFactor) >> 9;
    const rbx = (worldBorder.minX + (left + width) / scalingFactor) >> 9;
    const raz = (worldBorder.minZ + top / scalingFactor) >> 9;
    const rbz = (worldBorder.minZ + (top + height) / scalingFactor) >> 9;
    for (var rz = raz; rz <= rbz; rz += 1) {
	for (var rx = rax; rx <= rbx; rx += 1) {
	    var img = document.getElementById("r." + rx + "." + rz);
	    if (!img) continue;
	    const oldSrc = img.getAttribute("src");
	    if (oldSrc && oldSrc !== '') continue;
	    img.setAttribute("src", "/map/" + mapName + "/r." + rx + "." + rz + ".png");
	}
    }
}

var mouseDown = false;
var dragX = 0;
var dragY = 0;

window.onload = function() {
    const scrolling = document.getElementById("map_frame");
    const width = scrolling.clientWidth;
    const height = scrolling.clientHeight;
    scrolling.scrollTo(scalingFactor * (worldBorder.centerX - worldBorder.minX) - (width / 2),
		       scalingFactor * (worldBorder.centerZ - worldBorder.minZ) - (height / 2));
    scrolling.onscroll = event => calculateFrame();
    scrolling.onscrollend = event => calculateFrame();
    scrolling.onmousedown = event => {
	mouseDown = true;
	dragX = event.clientX;
	dragY = event.clientY;
    };
    scrolling.onmousemove = event => {
	if (!mouseDown) return;
	var x = event.clientX - dragX;
	var y = event.clientY - dragY;
	scrolling.scrollTo(scrolling.scrollLeft - x,
			   scrolling.scrollTop - y);
	dragX = event.clientX;
	dragY = event.clientY;
    };
    scrolling.onmouseup = event => {
	mouseDown = false;
    };
    scrolling.onmouseleave = event => {
	mouseDown = false;
    };
};

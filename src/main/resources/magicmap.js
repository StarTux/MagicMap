"use strict";
var mapName = "map_name";
var worldBorder = world_border;
const scalingFactor = scaling_factor;

function fixWorldBorder() {
    worldBorder.minX = (worldBorder.minX >> 9) << 9;
    worldBorder.maxX = (worldBorder.maxX >> 9) << 9;
    worldBorder.minZ = (worldBorder.minZ >> 9) << 9;
    worldBorder.maxZ = (worldBorder.maxZ >> 9) << 9;
}

fixWorldBorder();

function calculateFrame() {
    const scrolling = document.getElementById("map-frame");
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
            var img = document.getElementById("region-" + rx + "-" + rz);
            if (!img) continue;
            const oldSrc = img.getAttribute("src");
            if (oldSrc && oldSrc !== '') continue;
            img.src = '/map/' + mapName + '/r.' + rx + '.' + rz + '.png';
        }
    }
}

var mouseDown = false;
var dragX = 0;
var dragY = 0;

window.addEventListener('load', event => {
    const scrolling = document.getElementById('map-frame');
    const width = scrolling.clientWidth;
    const height = scrolling.clientHeight;
    scrolling.scrollTo(scalingFactor * (worldBorder.centerX - worldBorder.minX) - (width / 2),
                       scalingFactor * (worldBorder.centerZ - worldBorder.minZ) - (height / 2));
    scrolling.onscroll = event => calculateFrame();
    scrolling.onscrollend = event => calculateFrame();
    const mouseSurface = document;
    mouseSurface.onmousedown = event => {
        mouseDown = true;
        dragX = event.clientX;
        dragY = event.clientY;
        event.preventDefault();
    };
    mouseSurface.onmousemove = event => {
        if (!mouseDown) return;
        var x = event.clientX - dragX;
        var y = event.clientY - dragY;
        scrolling.scrollTo(scrolling.scrollLeft - x,
                           scrolling.scrollTop - y);
        dragX = event.clientX;
        dragY = event.clientY;
    };
    mouseSurface.onmouseup = event => {
        mouseDown = false;
    };
    mouseSurface.onmouseleave = event => {
        mouseDown = false;
    };
    websocket.addEventListener('websocketMessage', event => {
        switch (event.packet.id) {
        case 'chat': {
            const chatBox = document.getElementById('chat-box');
            const p = document.createElement('p');
            p.className = 'minecraft-chat-line';
            p.innerHTML = event.packet.html;
            chatBox.appendChild(p);
            chatBox.scrollTo(0, chatBox.scrollHeight);
            break;
        }
        case 'magicmap:player_update': {
            const player = document.getElementById('live-player-' + event.packet.player);
            if (!player) return;
            const left = event.packet.x - worldBorder.minX - 8;
            const top = event.packet.z - worldBorder.minZ - 8;
            player.style.top = scalingFactor * top + 'px';
            player.style.left = scalingFactor * left + 'px';
            break;
        }
        case 'magicmap:scroll_map': {
            const x = event.packet.x;
            const z = event.packet.z;
            scrollTo(x, z);
            break;
        }
        case 'magicmap:change_map': {
            mapName = event.packet.mapName;
            worldBorder = event.packet.worldBorder;
            fixWorldBorder();
            document.getElementById('map-frame').innerHTML = event.packet.innerHtml;
            document.title = event.packet.displayName;
            calculateFrame();
            sendServerMessage('did_change_map');
            break;
        }
        default: break;
        }
    });
});

function scrollTo(x, z) {
    const scrolling = document.getElementById('map-frame');
    const width = scrolling.clientWidth;
    const height = scrolling.clientHeight;
    scrolling.scrollTo(scalingFactor * (x - worldBorder.minX) - (width / 2),
                       scalingFactor * (z - worldBorder.minZ) - (height / 2));
}

function onClickPlayerList(element, event) {
    sendServerMessage('click_player_list', element.getAttribute('data-uuid'));
}

function onClickClaim(element, event) {
    console.log('click claim ' + element.getAttribute('data-claim-id') + ' ' + event);
}

function onClickLivePlayer(element, event) {
    console.log('click player ' + element.getAttribute('data-uuid') + ' ' + element.getAttribute('data-name'));
}

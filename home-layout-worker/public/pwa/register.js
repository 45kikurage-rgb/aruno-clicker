if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("/home-sw.js", { scope: "/home" }).catch((error) => {
    console.warn("Aruno-16Gridの Service Worker を登録できませんでした", error);
  });
}

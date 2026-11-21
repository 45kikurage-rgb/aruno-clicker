if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("/home-sw.js", { scope: "/home" }).catch((error) => {
    console.warn("ホーム配置の Service Worker を登録できませんでした", error);
  });
}

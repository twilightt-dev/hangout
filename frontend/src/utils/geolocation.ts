export interface UserLocation {
  longitude: number
  latitude: number
}

export function getCurrentLocation(): Promise<UserLocation> {
  if (!navigator.geolocation) {
    return Promise.reject(new Error('当前浏览器不支持定位'))
  }

  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      position => resolve({
        longitude: position.coords.longitude,
        latitude: position.coords.latitude,
      }),
      error => reject(new Error(error.message || '无法获取当前位置')),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 300000 },
    )
  })
}

export interface UserLocation {
  longitude: number
  latitude: number
  simulatedAddress?: string
}

// Approximate Hangzhou locations for local nearby-shop demonstrations.
const hangzhouLocations: UserLocation[] = [
  { longitude: 120.155, latitude: 30.253, simulatedAddress: '杭州湖滨一带' },
  { longitude: 120.164, latitude: 30.274, simulatedAddress: '杭州武林广场一带' },
  { longitude: 120.150, latitude: 30.334, simulatedAddress: '杭州拱墅区一带' },
]

export function getCurrentLocation(): Promise<UserLocation> {
  if (import.meta.env.DEV) {
    const index = Math.floor(Math.random() * hangzhouLocations.length)
    return Promise.resolve({ ...hangzhouLocations[index]! })
  }

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

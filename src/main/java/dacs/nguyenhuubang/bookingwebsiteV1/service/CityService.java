package dacs.nguyenhuubang.bookingwebsiteV1.service;

import dacs.nguyenhuubang.bookingwebsiteV1.entity.City;
import dacs.nguyenhuubang.bookingwebsiteV1.exception.CityNotFoundException;
import dacs.nguyenhuubang.bookingwebsiteV1.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CityService {
    private final CityRepository cityRepository;
    public List<City> getCities() {
        return (List<City>)cityRepository.findAll();
    }

    public City save(City city) {
        cityRepository.save(city);
        return city;
    }

    public City get(Integer id){
        Optional<City> result = cityRepository.findById(id);
        if (result.isPresent()){
            return result.get();
        }
        else
            throw new CityNotFoundException("Not found city with ID: "+id+"!");
    }

    public void delete(Integer id) {
        Long count = cityRepository.countById(id);
        if (count == null || count == 0) {
            throw new CityNotFoundException("Could not find any city with ID " + id);
        }
        cityRepository.deleteById(id);
    }

    public List<City> search(String keyword) {

        if (keyword != null) {
            return cityRepository.search(keyword);
        }
        return cityRepository.findAll();

    }

    public Page<City> findPaginated(int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize);
        return this.cityRepository.findAll(pageable);
    }

    public Page<City> findPaginated(int pageNo, int pageSize, String sortField, String sortDirection) {
        Sort sort = sortDirection.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortField).ascending() : Sort.by(sortField).descending();

        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, sort);
        return this.cityRepository.findAll(pageable);
    }

    public City findCityByName(String cityName) {
        return this.cityRepository.findCityByName(cityName);
    }

    public City getCityByName(String cityName) {

        return this.cityRepository.findCityByName(cityName);
    }
}

package com.myshop.modules.product.controller;

import com.myshop.modules.product.entity.ProductBrand;
import com.myshop.modules.product.service.ProductBrandService;
import com.myshop.modules.product.vo.ProductBrandVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/manager/product/brand")
public class ProductBrandManagerController {

    private final ProductBrandService productBrandService;

    public ProductBrandManagerController(
            ProductBrandService productBrandService) {
        this.productBrandService = productBrandService;
    }

    @GetMapping("/{id}")
    public ProductBrand getById(@PathVariable String id) {
        return productBrandService.getById(id);
    }

    @GetMapping("/all")
    public List<ProductBrand> getAll() {
        return productBrandService.getAll();
    }

    @PostMapping
    public boolean create(
            @Valid @RequestBody ProductBrandVO vo) {
        return productBrandService.create(vo);
    }

    @PutMapping("/{id}")
    public boolean update(
            @PathVariable String id,
            @Valid @RequestBody ProductBrandVO vo) {
        return productBrandService.update(id, vo);
    }

    @PutMapping("/disable/{id}")
    public boolean disable(
            @PathVariable String id,
            @RequestParam boolean disable) {
        return productBrandService.disable(id, disable);
    }


    
    @DeleteMapping("/{ids}")
    public boolean delete(
            @PathVariable String ids) {
        List<String> idList = Arrays.asList(ids.split(","));

        return productBrandService.deleteByIds(idList);
    }
}